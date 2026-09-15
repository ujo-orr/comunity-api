package org.example.communityapi.member;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.dto.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final MemberWithdrawalRepository memberWithdrawalRepository;

    // JWT 의존성
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public Long signUp(MemberSignUpRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (memberRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());
        // DTO를 엔터티로 변환 후 DB에 저장
        Member member = request.toEntity(encodedPassword);
        Member savedMember = memberRepository.save(member);
        // 생성된 회원의 ID(PK) 반환
        return savedMember.getId();
    }

    // 조회
    public MemberSearchResponse getMemberInfoByNickname(String nickname) {
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MemberSearchResponse.from(member);
    }

    // 내 정보 조회
    public MemberMyProfileResponse getMyProfileByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MemberMyProfileResponse.from(member);
    }

    // 수정
    @Transactional
    public void updateMyProfile(String currentMemberEmail, MemberUpdateRequest request) {
        // 1. 토큰에서 추출한 '내 이메일'로 회원 조회
        Member member = memberRepository.findByEmail(currentMemberEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 닉네임 처리 (값이 들어온 경우에만 중복 검사 및 변경 대상 지정)
        String newNickname = member.getNickname();
        if (org.springframework.util.StringUtils.hasText(request.nickname())) {
            String inputNickname = request.nickname();
            // 기존 닉네임과 다르고, 다른 사용자가 이미 사용 중이라면 예외 발생
            if (!member.getNickname().equals(inputNickname) && memberRepository.existsByNickname(inputNickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            newNickname = inputNickname;
        }

        // 3. 전화번호 처리 (값이 들어온 경우에만 중복 검사 및 변경 대상 지정)
        String newPhoneNumber = member.getPhoneNumber(); // 기본값: 기존 전화번호
        if (org.springframework.util.StringUtils.hasText(request.phoneNumber())) {
            String inputPhoneNumber = request.phoneNumber();
            // 기존 전화번호와 다르고, 다른 사용자가 이미 사용 중이라면 예외 발생
            if (!member.getPhoneNumber().equals(inputPhoneNumber) && memberRepository.existsByPhoneNumber(inputPhoneNumber)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
            }
            newPhoneNumber = inputPhoneNumber;
        }

        // 4. 비밀번호 처리 (값이 들어온 경우에만 암호화)
        String newPassword = member.getPassword(); // 기본값: 기존 비밀번호
        if (org.springframework.util.StringUtils.hasText(request.password())) {
            newPassword = passwordEncoder.encode(request.password());
        }

        // 5. 프로필 업데이트 (최종 결정된 값 전달)
        member.updateProfile(newNickname, newPhoneNumber, newPassword);
    }

    // 탈퇴
    @Transactional
    public void withdrawMember(String email, MemberWithdrawalRequest request) {
        // 1. 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 비밀번호 검증 (한 번 더 확인)
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 삭제 유예/보관 테이블에 INSERT
        MemberWithdrawal withdrawal = new MemberWithdrawal(member);
        memberWithdrawalRepository.save(withdrawal);

        // 4. member 테이블에서 해당 회원만 DELETE (Hard Delete)
        memberRepository.delete(member);
    }

    // 회원탈퇴 복구 (탈퇴 철회)
    @Transactional
    public void cancelWithdrawal(String email, MemberWithdrawalRequest request) {
        // 1. 탈퇴 유예 테이블에서 해당 이메일의 탈퇴 신청 기록 조회
        MemberWithdrawal withdrawal = memberWithdrawalRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 비밀번호 재검증 (유예 테이블에 보관된 암호화 비밀번호와 비교)
        if (!passwordEncoder.matches(request.password(), withdrawal.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 닉네임 중복 검사 (유예 기간 중 타인이 해당 닉네임으로 신규 가입했을 위험 방지)
        if (memberRepository.existsByNickname(withdrawal.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 4. 전화번호 중복 검사
        if (memberRepository.existsByPhoneNumber(withdrawal.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }

        // 5. Member 엔티티 복원
        Member restoredMember = Member.builder()
                .email(withdrawal.getEmail())
                .password(withdrawal.getPassword()) // 이미 암호화된 비밀번호 그대로 복구
                .nickname(withdrawal.getNickname())
                .phoneNumber(withdrawal.getPhoneNumber())
                .role(withdrawal.getRole())
                .build();

        memberRepository.save(restoredMember);

        // 6. 탈퇴 유예 테이블에서 백업 데이터 삭제 (철회 완료)
        memberWithdrawalRepository.delete(withdrawal);
    }
}
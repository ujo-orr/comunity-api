package org.example.communityapi.member;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.global.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class MemberService {
    private final MemberRepository memberRepository;
    private final MemberWithdrawalRepository memberWithdrawalRepository;

    // JWT 의존성
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // 회원가입
    @Transactional
    public Long signUp(MemberSignUpRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (memberRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        // DTO를 엔터티로 변환 후 DB에 저장
        Member member = request.toEntity(encodedPassword);
        Member savedMember = memberRepository.save(member);
        // 생성된 회원의 ID(PK) 반환
        return savedMember.getId();
    }

    // 조회
    public MemberResponse getMemberInfoByNickname(String nickname) {
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new MemberResponse(member);
    }

    // 내 정보 조회
    public MemberResponse getMyProfileByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new MemberResponse(member);
    }


    // 수정
    @Transactional
    public void updateMyProfile(String currentMemberEmail, MemberUpdateRequest request) {
        // 1. 토큰에서 추출한 '내 이메일'로 회원 조회
        Member member = memberRepository.findByEmail(currentMemberEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 닉네임 처리 (값이 들어온 경우에만 중복 검사 및 변경 대상 지정)
        String newNickname = member.getNickname();
        if (org.springframework.util.StringUtils.hasText(request.getNickname())) {
            String inputNickname = request.getNickname();
            // 기존 닉네임과 다르고, 다른 사용자가 이미 사용 중이라면 예외 발생
            if (!member.getNickname().equals(inputNickname) && memberRepository.existsByNickname(inputNickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            newNickname = inputNickname;
        }

        // 3. 전화번호 처리 (값이 들어온 경우에만 중복 검사 및 변경 대상 지정)
        String newPhoneNumber = member.getPhoneNumber(); // 기본값: 기존 전화번호
        if (org.springframework.util.StringUtils.hasText(request.getPhoneNumber())) {
            String inputPhoneNumber = request.getPhoneNumber();
            // 기존 전화번호와 다르고, 다른 사용자가 이미 사용 중이라면 예외 발생
            if (!member.getPhoneNumber().equals(inputPhoneNumber) && memberRepository.existsByPhoneNumber(inputPhoneNumber)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
            }
            newPhoneNumber = inputPhoneNumber;
        }

        // 4. 비밀번호 처리 (값이 들어온 경우에만 암호화)
        String newPassword = member.getPassword(); // 기본값: 기존 비밀번호
        if (org.springframework.util.StringUtils.hasText(request.getPassword())) {
            newPassword = passwordEncoder.encode(request.getPassword());
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
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 삭제 유예/보관 테이블에 INSERT
        MemberWithdrawal withdrawal = new MemberWithdrawal(member);
        memberWithdrawalRepository.save(withdrawal);

        // 4. member 테이블에서 해당 회원만 DELETE (Hard Delete)
        memberRepository.delete(member);
    }

    // 로그인
    @Transactional(readOnly = true)
    public MemberLoginResponse login(MemberLoginRequest request) {
        // 1. Member 테이블 조회
        Optional<Member> memberOpt = memberRepository.findByEmail(request.getEmail());

        if (memberOpt.isPresent()) {
            Member member = memberOpt.get();
            if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            // 정상 로그인: JWT 토큰 생성 및 반환
            String accessToken = jwtTokenProvider.createToken(member.getEmail());
            return MemberLoginResponse.success(accessToken);
        }

        // 2. Member에는 없지만 탈퇴 유예(MemberWithdrawal) 테이블에 있는지 확인
        Optional<MemberWithdrawal> withdrawalOpt = memberWithdrawalRepository.findByEmail(request.getEmail());

        if (withdrawalOpt.isPresent()) {
            MemberWithdrawal withdrawal = withdrawalOpt.get();

            // 유예 계정의 비밀번호 검증
            if (!passwordEncoder.matches(request.getPassword(), withdrawal.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            // 비밀번호까지 맞다면 '탈퇴 유예 상태'임을 나타내는 특별한 응답 반환
            return MemberLoginResponse.withdrawalPending();
            // 또는 Custom Exception(e.g., WithdrawalPendingException)을 던져 GlobalExceptionHandler에서 처리
        }

        // 3. 둘 다 없으면 정말 존재하지 않는 회원
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
}
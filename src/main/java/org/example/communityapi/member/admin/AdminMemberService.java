package org.example.communityapi.member.admin;

import lombok.RequiredArgsConstructor;

import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.auth.repository.RefreshTokenRepository;
import org.example.communityapi.member.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final MemberWithdrawalRepository memberWithdrawalRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // 전체 회원 목록 조회 (관리자용 DTO 반환)
    public List<AdminMemberResponse> findAllMembers() {
        return memberRepository.findAll().stream()
                .map(AdminMemberResponse::from)
                .toList();
    }

    // 회원 조회
    public List<AdminMemberResponse> searchMembers(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return memberRepository.findAll().stream()
                    .map(AdminMemberResponse::from)
                    .toList();
        }

        // 이메일, 닉네임, 전화번호 중 하나라도 포함(Containing)되어 있으면 조회
        return memberRepository.searchByKeyword(keyword)
                .stream()
                .map(AdminMemberResponse::from)
                .toList();
    }

    // 회원탈퇴 복구 (탈퇴 철회)
    @Transactional
    public void cancelWithdrawal(Long id) {
        // 탈퇴 유예 테이블에서 해당 ID의 탈퇴 신청 기록 조회
        MemberWithdrawal withdrawal = memberWithdrawalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 닉네임 중복 검사 (유예 기간 중 타인이 해당 닉네임으로 신규 가입했을 위험 방지)
        if (memberRepository.existsByNickname(withdrawal.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 전화번호 중복 검사
        if (memberRepository.existsByPhoneNumber(withdrawal.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }

        // Member 엔티티 복원 (Hard Delete 되었으므로 백업된 정보로 새로 생성, 새 Auto Increment ID 부여됨)
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

    @Transactional
    public void banMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 자기 자신이나 다른 관리자를 차단하는 예외 케이스 처리
        if (member.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.BAN_DENIED);
        }

        member.ban();

        refreshTokenRepository.deleteByEmail(member.getEmail());
    }

    @Transactional
    public void unbanMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        member.unban();
    }
}
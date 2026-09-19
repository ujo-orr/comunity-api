package org.example.communityapi.member.admin;

import lombok.RequiredArgsConstructor;

import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.auth.repository.RefreshTokenRepository;
import org.example.communityapi.member.*;
import org.example.communityapi.member.admin.dto.AdminMemberResponse;
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

    public List<AdminMemberResponse> findAllMembers() {
        return memberRepository.findAllAdminMemberResponses();
    }

    public List<AdminMemberResponse> searchMembers(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return memberRepository.findAllAdminMemberResponses();
        }

        return memberRepository.searchAdminMemberResponsesByKeyword(keyword);
    }

    @Transactional
    public void cancelWithdrawal(Long id) {
        MemberWithdrawal withdrawal = memberWithdrawalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (memberRepository.existsByEmail(withdrawal.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (memberRepository.existsByNickname(withdrawal.getNickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        if (memberRepository.existsByPhoneNumber(withdrawal.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }

        memberRepository.save(withdrawal.toMember());

        memberWithdrawalRepository.delete(withdrawal);
    }

    @Transactional
    public void banMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (member.getRole() != Role.USER) {
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

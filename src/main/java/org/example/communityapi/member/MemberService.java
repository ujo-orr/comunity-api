package org.example.communityapi.member;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.auth.repository.RefreshTokenRepository;
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
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long signUp(MemberSignUpRequest request) {
        // 탈퇴 유예 중인 이메일은 복구할 수 있도록 남겨 둔다.
        if (memberRepository.existsByEmail(request.email())
                || memberWithdrawalRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (memberRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = request.toEntity(encodedPassword);
        Member savedMember = memberRepository.save(member);
        return savedMember.getId();
    }

    public MemberSearchResponse getMemberInfoByNickname(String nickname) {
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MemberSearchResponse.from(member);
    }

    public MemberMyProfileResponse getMyProfileByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MemberMyProfileResponse.from(member);
    }

    @Transactional
    public void updateMyProfile(String currentMemberEmail, MemberUpdateRequest request) {
        Member member = memberRepository.findByEmail(currentMemberEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newNickname = member.getNickname();
        if (org.springframework.util.StringUtils.hasText(request.nickname())) {
            String inputNickname = request.nickname();
            if (!member.getNickname().equals(inputNickname) && memberRepository.existsByNickname(inputNickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            newNickname = inputNickname;
        }

        String newPhoneNumber = member.getPhoneNumber();
        if (org.springframework.util.StringUtils.hasText(request.phoneNumber())) {
            String inputPhoneNumber = request.phoneNumber();
            if (!member.getPhoneNumber().equals(inputPhoneNumber) && memberRepository.existsByPhoneNumber(inputPhoneNumber)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
            }
            newPhoneNumber = inputPhoneNumber;
        }

        String newPassword = member.getPassword();
        if (org.springframework.util.StringUtils.hasText(request.password())
                && !passwordEncoder.matches(request.password(), member.getPassword())) {

            newPassword = passwordEncoder.encode(request.password());
            refreshTokenRepository.deleteByEmail(member.getEmail());
        }
        member.updateProfile(newNickname, newPhoneNumber, newPassword);
    }

    @Transactional
    public void withdrawMember(String email, MemberWithdrawalRequest request) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        MemberWithdrawal withdrawal = new MemberWithdrawal(member);
        memberWithdrawalRepository.save(withdrawal);

        // 탈퇴한 계정의 토큰은 다시 발급하지 않는다.
        refreshTokenRepository.deleteByEmail(email);

        memberRepository.delete(member);
    }

    @Transactional
    public void cancelWithdrawal(String email, MemberWithdrawalRequest request) {
        MemberWithdrawal withdrawal = memberWithdrawalRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), withdrawal.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (memberRepository.existsByEmail(email)) {
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
}

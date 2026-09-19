package org.example.communityapi.member.superAdmin;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SuperAdminService {
    private final MemberRepository memberRepository;

    @Transactional
    public void updateRole(Long targetId, Role newRole, String requesterEmail) {

        Member requester = memberRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (targetId.equals(requester.getId()) && newRole != Role.SUPERADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_DEMOTE_LAST_SUPERADMIN);
        }

        Member targetMember = memberRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        targetMember.changeRole(newRole);
    }
}

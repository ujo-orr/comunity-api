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

    // 특정 회원 권한 변경 (요청자의 Role을 파라미터로 함께 전달받음)
    @Transactional
    public void updateRole(Long targetId, Role newRole, String requesterEmail) { // 👈 Long -> String 변경

        // 요청자(SUPERADMIN) 조회
        Member requester = memberRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 셀프 강등 방지 검증
        if (targetId.equals(requester.getId()) && newRole != Role.SUPERADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_DEMOTE_LAST_SUPERADMIN);
        }

        // 대상 회원 권한 변경
        Member targetMember = memberRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        targetMember.changeRole(newRole);
    }
}

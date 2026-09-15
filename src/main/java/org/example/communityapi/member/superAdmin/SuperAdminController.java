package org.example.communityapi.member.superAdmin;

import lombok.RequiredArgsConstructor;

import org.example.communityapi.member.superAdmin.dto.SuperAdminMemberRoleRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    // 회원/관리자의 Role 변경은 SUPERADMIN 전용 API로 분리
    @PatchMapping("/members/{id}/role")
    public ResponseEntity<Void> updateRole(
            @PathVariable Long id,
            @RequestBody SuperAdminMemberRoleRequest request,
            @AuthenticationPrincipal User loginUser
    ) {
        // UserDetails의 getUsername()은 시큐리티 인증 시 넣었던 식별자(이메일 또는 String으로 변환된 Member ID)를 반환합니다.
        String email = loginUser.getUsername();

        superAdminService.updateRole(id, request.role(), email);
        return ResponseEntity.ok().build();
    }
}

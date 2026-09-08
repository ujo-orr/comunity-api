package org.example.communityapi.member;

import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPERADMIN')")
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

        superAdminService.updateRole(id, request.getRole(), email);
        return ResponseEntity.ok().build();
    }
}

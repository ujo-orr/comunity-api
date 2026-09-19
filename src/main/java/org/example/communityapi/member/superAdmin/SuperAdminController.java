package org.example.communityapi.member.superAdmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

    @PatchMapping("/members/{id}/role")
    public ResponseEntity<Void> updateRole(
            @Positive @PathVariable Long id,
            @Valid @RequestBody SuperAdminMemberRoleRequest request,
            @AuthenticationPrincipal User loginUser
    ) {
        String email = loginUser.getUsername();

        superAdminService.updateRole(id, request.role(), email);
        return ResponseEntity.ok().build();
    }
}

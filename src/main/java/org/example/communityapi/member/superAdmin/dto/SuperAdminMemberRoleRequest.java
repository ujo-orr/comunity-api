package org.example.communityapi.member.superAdmin.dto;

import jakarta.validation.constraints.NotNull;
import org.example.communityapi.member.Role;

public record SuperAdminMemberRoleRequest(
        @NotNull(message = "권한은 필수 선택 항목입니다.")
        Role role
) {}

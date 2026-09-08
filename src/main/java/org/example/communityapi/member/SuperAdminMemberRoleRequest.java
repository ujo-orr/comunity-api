package org.example.communityapi.member;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuperAdminMemberRoleRequest {

    @NotBlank(message = "권한을 필수 선택 항목 입니다.")
    private Role role;
}

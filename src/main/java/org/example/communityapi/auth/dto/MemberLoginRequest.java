package org.example.communityapi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberLoginRequest(
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Size(max = 30, message = "이메일은 30자 이하로 입력해주세요.")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$",
            message = "올바른 이메일 형식을 입력해주세요."
    )
    String email,

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(max = 72, message = "비밀번호는 72자 이하로 입력해주세요.")
    String password
) {}

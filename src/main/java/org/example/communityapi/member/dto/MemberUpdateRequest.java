package org.example.communityapi.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MemberUpdateRequest (
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9]{2,10}$",
                message = "닉네임은 한글, 영문, 숫자를 조합하여 2~10자로 입력해주세요."
        )
        String nickname,

        @Pattern(
                regexp = "^010[0-9]{8}$",
                message = "-를 제외한 올바른 010 휴대폰 번호 11자리를 입력해주세요."
        )
        String phoneNumber,

        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
                message = "비밀번호는 대문자 1개 이상을 포함한 영문, 숫자, 특수문자를 포함하여 8~20자로 입력해주세요."
        )
        String password
) {}

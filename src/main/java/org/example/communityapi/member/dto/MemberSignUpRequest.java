package org.example.communityapi.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.communityapi.member.Member;

@Getter
@NoArgsConstructor

public class MemberSignUpRequest {
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$",
            message = "올바른 이메일 형식을 입력해주세요."
    )
    private  String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
            message = "비밀번호는 대문자 1개 이상을 포함한 영문, 숫자, 특수문자를 포함하여 8~20자로 입력해주세요."
    )
    private  String password;

    @NotBlank(message = "휴대폰 번호는 필수 입력 항목입니다.")
    @Pattern(
            regexp = "^010[0-9]{8}$",
            message = "-를 제외한 올바른 010 휴대폰 번호 11자리를 입력해주세요."
    )
    private  String phoneNumber;

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    @Pattern(
            regexp = "^[가-힣a-zA-Z0-9]{2,10}$",
            message = "닉네임은 한글, 영문, 숫자를 조합하여 2~10자로 입력해주세요."
    )
    private  String nickname;

    // DTO 기반 Member 엔터티 생성
    public Member toEntity(String encodedPassword) {
        return Member.builder()
                .email(this.email)
                .password(encodedPassword)
                .nickname(this.nickname)
                .phoneNumber(this.phoneNumber)
                .build();
    }
}

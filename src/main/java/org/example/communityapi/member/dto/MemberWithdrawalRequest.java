package org.example.communityapi.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberWithdrawalRequest {
    @NotBlank(message = "비밀번호 입력은 필수 입니다.")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
            message = "비밀번호는 대문자 1개 이상을 포함한 영문, 숫자, 특수문자를 포함하여 8~20자로 입력해주세요."
    )
    private String password; // 탈퇴 확인용 비밀번호

    public MemberWithdrawalRequest(String password) {this.password = password;}
}

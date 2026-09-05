package org.example.communityapi.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberWithdrawalRequest {
    @NotBlank(message = "비밀번호 입력은 필수 입니다.")
    private String password; // 탈퇴 확인용 비밀번호

    public MemberWithdrawalRequest(String password) {this.password = password;}
}

package org.example.comunityapi.member;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminMemberUpdateRequest {
    private String nickname;
    private String phoneNumber;
    private String password;
    private Role role;
}
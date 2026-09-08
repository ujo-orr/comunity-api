package org.example.communityapi.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberStatus {
    ACTIVE("정상"),
    BANNED("차단됨");

    private final String description;
}

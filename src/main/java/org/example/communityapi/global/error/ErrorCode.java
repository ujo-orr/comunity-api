package org.example.communityapi.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request 검증 실패
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "적절하지 않은 요청 값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 HTTP 메서드입니다."),
    CANNOT_DEMOTE_LAST_SUPERADMIN(HttpStatus.BAD_REQUEST,"C003","SUPERADMIN은 권한을 변경할 수 없습니다."),

    // 401 Unauthorized 로그인 실패
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A002", "인증이 필요하거나 유효하지 않은 토큰입니다."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A003", "해당 리소스에 접근할 권한이 없습니다."),
    BAN_DENIED(HttpStatus.FORBIDDEN,"A004","관리자 계정은 차단할 수 없습니다."),
    BANNED_USER(HttpStatus.FORBIDDEN,"A005", "관리자에 의해 이용이 제한된 계정 입니다."),

    // 404 Not Found 존재 X
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "존재하지 않는 회원입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "존재하지 않는 카테고리입니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "E002", "존재하지 않는 게시글입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "E005", "존재하지 않는 댓글입니다."),

    // 409 Conflict 중복
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 존재하는 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "U003", "이미 존재하는 닉네임입니다."),
    DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, "U004", "이미 존재하는 휴대폰 번호 입니다."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "E003", "이미 존재하는 카테고리 입니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "E004", "사용중인 카테고리 입니다."),
    ALREADY_LIKED_POST(HttpStatus.CONFLICT, "E006", "이미 좋아요한 게시글입니다."),
    POST_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "E007", "좋아요 기록이 존재하지 않습니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "서버 내부 에러가 발생했습니다. 관리자에게 문의하세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

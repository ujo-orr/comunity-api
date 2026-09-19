package org.example.communityapi.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    EMPTY_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "C009", "검색어를 입력해 주세요."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "적절하지 않은 요청 값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 HTTP 메서드입니다."),
    CANNOT_DEMOTE_LAST_SUPERADMIN(HttpStatus.BAD_REQUEST,"C003","SUPERADMIN은 권한을 변경할 수 없습니다."),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "요청한 리소스를 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "C005", "지원하지 않는 Content-Type입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "C006", "요청한 응답 형식을 지원하지 않습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "E010", "업로드 가능한 파일 크기를 초과했습니다."),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A002", "인증이 필요하거나 유효하지 않은 토큰입니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A003", "해당 리소스에 접근할 권한이 없습니다."),
    BAN_DENIED(HttpStatus.FORBIDDEN,"A004","관리자 계정은 차단할 수 없습니다."),
    BANNED_USER(HttpStatus.FORBIDDEN,"A005", "관리자에 의해 이용이 제한된 계정 입니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "존재하지 않는 회원입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "존재하지 않는 카테고리입니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "E002", "존재하지 않는 게시글입니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "E008", "존재하지 않는 첨부파일입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "E005", "존재하지 않는 댓글입니다."),

    INVALID_FILE(HttpStatus.BAD_REQUEST, "E009", "첨부할 수 없는 파일입니다."),

    DATA_CONFLICT(HttpStatus.CONFLICT, "C007", "이미 존재하거나 다른 데이터에서 참조 중인 리소스입니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "C008", "다른 요청과 충돌했습니다. 최신 상태를 확인한 뒤 다시 시도해 주세요."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 존재하는 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "U003", "이미 존재하는 닉네임입니다."),
    DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, "U004", "이미 존재하는 휴대폰 번호 입니다."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "E003", "이미 존재하는 카테고리 입니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "E004", "사용중인 카테고리 입니다."),
    ALREADY_LIKED_POST(HttpStatus.CONFLICT, "E006", "이미 좋아요한 게시글입니다."),
    POST_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "E007", "좋아요 기록이 존재하지 않습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "서버 내부 에러가 발생했습니다. 관리자에게 문의하세요."),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S002", "파일을 저장하거나 읽는 중 오류가 발생했습니다."),
    AUTHENTICATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S003", "인증 서비스를 일시적으로 사용할 수 없습니다."),
    STORAGE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S004", "저장소를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

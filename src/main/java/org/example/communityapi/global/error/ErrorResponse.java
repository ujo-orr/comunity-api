package org.example.communityapi.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.validation.BindingResult;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String code;
    private final String message;
    private final List<FieldErrorDetail> errors;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .errors(FieldErrorDetail.of(bindingResult))
                .build();
    }

    @Getter
    @Builder
    public static class FieldErrorDetail {
        private final String field;
        private final String value;
        private final String reason;

        // 비밀번호 및 토큰 노출 방지를 위한 응답 입력값 제외
        public static List<FieldErrorDetail> of(BindingResult bindingResult) {
            return bindingResult.getFieldErrors().stream()
                    .map(error -> FieldErrorDetail.builder()
                            .field(error.getField())
                            .value("")
                            .reason(error.getDefaultMessage())
                            .build())
                    .toList();
        }
    }
}
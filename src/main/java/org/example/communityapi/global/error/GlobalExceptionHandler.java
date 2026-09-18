package org.example.communityapi.global.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Spring MVC가 결정한 상태 코드와 Allow 등의 헤더를 유지하면서 응답 형식을 통일한다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        ErrorCode errorCode = switch (status.value()) {
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError()
                    ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.INVALID_INPUT_VALUE;
        };
        if (e instanceof MissingServletRequestPartException) {
            errorCode = ErrorCode.INVALID_FILE;
        }
        if (status.is5xxServerError()) {
            log.error("Unexpected MVC exception", e);
        }
        ErrorResponse response = e instanceof BindException bindingException
                ? ErrorResponse.of(errorCode, bindingException.getBindingResult())
                : ErrorResponse.of(errorCode);
        // 오류 응답은 Accept 값과 무관하게 API 공통 JSON 형식으로 제공한다.
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(e, response, responseHeaders, status, request);
    }

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        if (e.getErrorCode().getStatus().is5xxServerError()) {
            log.error("Business operation failed: {}", e.getErrorCode().getCode(), e);
        }
        return response(e.getErrorCode());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        return response(ErrorCode.INVALID_INPUT_VALUE);
    }

    // 메서드 보안에서 발생한 예외도 필터의 인증/인가 실패와 같은 형식으로 반환한다.
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return response(ErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler(AuthenticationException.class)
    protected ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return response(ErrorCode.UNAUTHORIZED);
    }

    // 트랜잭션 커밋 시점의 실패도 서비스 밖에서 여기로 전달된다.
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    protected ResponseEntity<ErrorResponse> handleStorageException(RuntimeException e) {
        ErrorCode errorCode = StorageExceptionClassifier.classify(e);
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("Storage operation failed: {}", errorCode.getCode(), e);
        } else {
            log.warn("Storage conflict: {}", errorCode.getCode());
        }
        return response(errorCode);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return response(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_JSON).body(ErrorResponse.of(errorCode));
    }
}

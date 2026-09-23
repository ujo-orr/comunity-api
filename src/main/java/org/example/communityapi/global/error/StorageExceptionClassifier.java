package org.example.communityapi.global.error;

import org.springframework.dao.*;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// DB 오류를 사용자에게 보낼 오류 코드로 변환
public final class StorageExceptionClassifier {
    private StorageExceptionClassifier() {}

    public static ErrorCode classify(Throwable exception) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof ConcurrencyFailureException) {
                return ErrorCode.CONCURRENT_MODIFICATION;
            }
            if (cause instanceof DuplicateKeyException) {
                return ErrorCode.DATA_CONFLICT;
            }
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof TransientDataAccessResourceException
                    || cause instanceof QueryTimeoutException) {
                return ErrorCode.STORAGE_SERVICE_UNAVAILABLE;
            }
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                // 중복값 또는 외래 키 오류 확인
                if ("23505".equals(state) || "23503".equals(state) || "23506".equals(state)
                        || ("23000".equals(state) && Set.of(1062, 1451, 1452).contains(sql.getErrorCode()))) {
                    return ErrorCode.DATA_CONFLICT;
                }
                if (state != null && state.startsWith("40")) {
                    return ErrorCode.CONCURRENT_MODIFICATION;
                }
                if (state != null && state.startsWith("08")) {
                    return ErrorCode.STORAGE_SERVICE_UNAVAILABLE;
                }
            }
        }
        if (exception instanceof CannotCreateTransactionException) {
            return ErrorCode.STORAGE_SERVICE_UNAVAILABLE;
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }
}

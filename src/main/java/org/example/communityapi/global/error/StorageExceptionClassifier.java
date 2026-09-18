package org.example.communityapi.global.error;

import org.springframework.dao.*;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 저장소 예외의 메시지를 노출하지 않고 원인 타입/SQL 코드로 분류한다. */
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
                // H2/PostgreSQL 및 MySQL의 유일성/외래키 충돌만 409로 분류한다.
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
        // NOT NULL 위반, SQL 문법 오류 등은 클라이언트 충돌로 위장하지 않는다.
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }
}

package org.example.communityapi.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class StorageExceptionHandlingTest {
    private final FailureController controller = new FailureController();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @DisplayName("저장소 장애는 503으로 충돌은 409로 내부 오류는 500으로 응답하며 내부 정보를 노출하지 않는다")
    @MethodSource("failures")
    void classifiesFailuresWithoutExposingStorageDetails(RuntimeException exception, int status, String code)
            throws Exception {
        controller.failure = exception;
        mvc.perform(get("/storage-failure"))
                .andExpect(status().is(status)).andExpect(jsonPath("code").value(code))
                .andExpect(content().string(not(containsString("internal-secret"))));
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new RedisConnectionFailureException("internal-secret"), 503, "S004"),
                Arguments.of(new DataAccessResourceFailureException("internal-secret"), 503, "S004"),
                Arguments.of(new QueryTimeoutException("internal-secret"), 503, "S004"),
                Arguments.of(new CannotCreateTransactionException("internal-secret"), 503, "S004"),
                Arguments.of(new OptimisticLockingFailureException("internal-secret"), 409, "C008"),
                Arguments.of(new CannotAcquireLockException("internal-secret"), 409, "C008"),
                Arguments.of(new DuplicateKeyException("internal-secret"), 409, "C007"),
                Arguments.of(integrity("23505", 0), 409, "C007"),
                Arguments.of(integrity("23000", 1062), 409, "C007"),
                Arguments.of(integrity("23000", 1451), 409, "C007"),
                Arguments.of(integrity("23000", 1452), 409, "C007"),
                Arguments.of(new TransactionSystemException("internal-secret", integrity("23505", 0)), 409, "C007"),
                Arguments.of(new TransactionSystemException("internal-secret", new SQLException("internal-secret", "40001")), 409, "C008"),
                Arguments.of(new TransactionSystemException("internal-secret", new SQLException("internal-secret", "08006")), 503, "S004"),
                Arguments.of(integrity("23502", 0), 500, "S001"),
                Arguments.of(integrity("23000", 1048), 500, "S001"),
                Arguments.of(new InvalidDataAccessResourceUsageException("internal-secret"), 500, "S001")
        );
    }

    private static DataIntegrityViolationException integrity(String state, int vendorCode) {
        return new DataIntegrityViolationException("internal-secret", new SQLException("internal-secret", state, vendorCode));
    }

    @RestController
    static class FailureController {
        RuntimeException failure;
        @GetMapping("/storage-failure")
        void fail() { throw failure; }
    }
}

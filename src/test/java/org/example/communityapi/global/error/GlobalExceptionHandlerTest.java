package org.example.communityapi.global.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @DisplayName("요청 본문이 없거나 형식이 잘못되면 400을 반환한다")
    @ValueSource(strings = {"", "{", "{\"password\":{}}"})
    void missingOrMalformedBodyReturns400(String body) throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("C001"));
    }

    @Test
    @DisplayName("입력값 검증에 실패하면 비밀값을 노출하지 않고 필드와 사유를 포함한 400을 반환한다")
    void validationPreservesFieldAndReasonWithoutEchoingSecrets() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("errors[0].field").value("password"))
                .andExpect(jsonPath("errors[0].reason").isNotEmpty())
                .andExpect(jsonPath("errors[0].value").value(""))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @DisplayName("필수 파라미터가 없거나 값이 유효하지 않으면 400을 반환한다")
    void missingAndInvalidParametersReturn400() throws Exception {
        mvc.perform(get("/test/parameter"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("C001"));
        mvc.perform(get("/test/parameter").param("id", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("C001"));
        mvc.perform(get("/test/parameter").param("id", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("C001"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드로 요청하면 허용 메서드 헤더와 함께 405를 반환한다")
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(delete("/test/body"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("code").value("C002"))
                .andExpect(header().string("Allow", containsString("POST")));
    }

    @Test
    @DisplayName("지원하지 않는 요청 콘텐츠 타입이면 415를 반환한다")
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("code").value("C005"));
    }

    @Test
    @DisplayName("지원하지 않는 응답 콘텐츠 타입을 요청하면 406을 반환한다")
    void unsupportedResponseTypeReturns406() throws Exception {
        mvc.perform(get("/test/json").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("code").value("C006"));
    }

    @Test
    @DisplayName("존재하지 않는 리소스를 요청하면 404를 반환한다")
    void missingResourceReturns404() throws Exception {
        mvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("C004"));
    }

    @Test
    @DisplayName("첨부파일이 누락되면 400을 반환하고 업로드 용량을 초과하면 413을 반환한다")
    void missingFileAndOversizedUploadHaveDistinctErrors() throws Exception {
        mvc.perform(multipart("/test/file"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("E009"));
        mvc.perform(get("/test/oversized"))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("code").value("E010"));
    }

    @Test
    @DisplayName("인증에 실패하면 401을 반환하고 접근 권한이 없으면 403을 반환한다")
    void securityExceptionsRetain401And403() throws Exception {
        mvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("A002"));
        mvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("A003"));
    }

    @Test
    @DisplayName("게시글 없음 오류가 발생하면 기존 에러 코드와 함께 404를 반환한다")
    void businessExceptionRetainsExistingCode() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("E002"));
    }

    @Test
    @DisplayName("예상하지 못한 오류가 발생하면 내부 정보를 노출하지 않고 500을 반환한다")
    void unexpectedFailureDoesNotLeakInternalDetails() throws Exception {
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("code").value("S001"))
                .andExpect(content().string(not(containsString("database-password"))));
    }

    @Test
    @DisplayName("응답값 검증에 실패하면 500을 반환한다")
    void returnValueValidationIsServerError() throws Exception {
        mvc.perform(get("/test/invalid-return"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("code").value("S001"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {
        record Payload(@NotBlank @Size(min = 8) String password) {}

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        void body(@Valid @RequestBody Payload payload) {}

        @GetMapping("/parameter")
        void parameter(@RequestParam @Min(1) Long id) {}

        @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
        Payload json() { return new Payload("valid-password"); }

        @GetMapping("/missing")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "missing");
        }

        @PostMapping("/file")
        void file(@RequestPart MultipartFile file) {}

        @GetMapping("/oversized")
        void oversized() { throw new MaxUploadSizeExceededException(1024); }

        @GetMapping("/unauthorized")
        void unauthorized() { throw new BadCredentialsException("internal detail"); }

        @GetMapping("/forbidden")
        void forbidden() { throw new AccessDeniedException("internal detail"); }

        @GetMapping("/business")
        void business() { throw new BusinessException(ErrorCode.POST_NOT_FOUND); }

        @GetMapping("/unexpected")
        void unexpected() { throw new IllegalStateException("database-password"); }

        @NotBlank
        @GetMapping("/invalid-return")
        String invalidReturn() { return ""; }
    }
}

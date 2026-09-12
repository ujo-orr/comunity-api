package org.example.communityapi.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.auth.dto.AuthLoginResponse;
import org.example.communityapi.auth.dto.RefreshTokenRequest;
import org.example.communityapi.auth.dto.TokenResponse;
import org.example.communityapi.auth.dto.MemberLoginRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Access Token 재발급 요청
    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = authService.reissue(request.getRefreshToken());
        return ResponseEntity.ok(tokenResponse);
    }

    // 로그인 API (토큰)
    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(
            @Valid
            @RequestBody
            MemberLoginRequest request) {
        AuthLoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // 로그아웃 요청 (인증된 회원만 접근 가능)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // Authorization 헤더에서 Bearer 토큰 추출
        String bearerToken = request.getHeader("Authorization");
        String accessToken = null;

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            accessToken = bearerToken.substring(7);
        }

        authService.logout(accessToken);
        return ResponseEntity.ok().build();
    }
}

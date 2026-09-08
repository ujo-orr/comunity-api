package org.example.communityapi.member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor

public class MemberController {
    private final MemberService memberService;

    // 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(
            @Valid @RequestBody MemberSignUpRequest request) {
        Long memberId = memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberId);
    }

    // 로그인 API (토큰)
    @PostMapping("/login")
    public ResponseEntity<MemberLoginResponse> login(
            @Valid
            @RequestBody
            MemberLoginRequest request) {
        MemberLoginResponse response = memberService.login(request);
        return ResponseEntity.ok(response);
    }

    // 닉네임 기준 회원 조회 API
    @GetMapping("/search")
    public ResponseEntity<MemberResponse> getMemberProfile(@RequestParam String nickname) {
        return ResponseEntity.ok(memberService.getMemberInfoByNickname(nickname));
    }

    // 내 정보 조회 API (토큰)
    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyInfo(@AuthenticationPrincipal String email) {
        // Service에서 MemberResponse를 반환받도록 호출
        MyProfileResponse response = memberService.getMyProfileByEmail(email);
        return ResponseEntity.ok(response);
    }

    // 내 정보 수정 API (토큰)
    @PatchMapping("/me")
    public ResponseEntity<Void> updateMyProfile(
            @AuthenticationPrincipal String email,
            @Valid
            @RequestBody MemberUpdateRequest request
    ) {
        memberService.updateMyProfile(email, request);
        return ResponseEntity.ok().build();
    }

    // 본인 회원 탈퇴 (토큰)
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdrawMember(
            Authentication authentication,
            @Valid
            @RequestBody MemberWithdrawalRequest request) {

        String email = authentication.getName();

        memberService.withdrawMember(email, request);
        return ResponseEntity.ok().build();
    }
}

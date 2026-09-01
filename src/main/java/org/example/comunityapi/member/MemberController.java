package org.example.comunityapi.member;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor

public class MemberController {
    private final MemberService memberService;

    // 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@RequestBody MemberSignUpRequest request) {
        Long memberId = memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberId);
    }

    // 1. ID 기준 회원 조회 API
    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> getMemberInfo(@PathVariable Long id) {
        MemberResponse response = memberService.getMemberInfo(id);
        return ResponseEntity.ok(response);
    }
    // 2. 닉네임 기준 회원 조회 API
    @GetMapping("/nickname/{nickname}")
    public ResponseEntity<MemberResponse> getMemberInfoByNickname(@PathVariable String nickname) {
        MemberResponse response = memberService.getMemberInfoByNickname(nickname);
        return ResponseEntity.ok(response);
    }

    // 내 정보 조회 API (토큰)
    @GetMapping("/me")
    public ResponseEntity<MemberResponse> getMyInfo(Authentication authentication) {
        // JwtAuthenticationFilter에서 저장한 email 추출 (authentication.getName())
        String email = authentication.getName();

        // 이메일 기반 회원 정보 조회 (Service에 이메일 조회 메서드가 구현되어 있어야 함)
        MemberResponse response = memberService.getMemberInfoByEmail(email);
        return ResponseEntity.ok(response);
    }

    // 내 정보 수정 API (토큰)
    @PutMapping("/me")
    public ResponseEntity<Void> updateMyInfo(
            Authentication authentication,
            @RequestBody MemberUpdateRequest request) {

        // 토큰 속에 들어있는 이메일 추출
        String email = authentication.getName();

        memberService.updateMemberByEmail(email, request);
        return ResponseEntity.ok().build();
    }

    /* 관리자 권한?
    // 회원 탈퇴 API
    @DeleteMapping("/email/{email}")
    public ResponseEntity<Void> deleteMemberByEmail(
            @PathVariable String email,
            @RequestBody MemberWithdrawalRequest request) {

        memberService.withdrawMember(email, request);
        return ResponseEntity.ok().build();
    }
    */


        // 로그인 API (토큰)
        @PostMapping("/login")
        public ResponseEntity<String> login(@RequestBody MemberLoginRequest request) {
            String token = memberService.login(request);
            return ResponseEntity.ok(token);
        }

        // 본인 회원 탈퇴 (토큰)
        @DeleteMapping("/me")
        public ResponseEntity<Void> withdrawMember(
                Authentication authentication,
                @RequestBody MemberWithdrawalRequest request) {

            String email = authentication.getName();

            memberService.withdrawMember(email, request);
            return ResponseEntity.ok().build();
        }

}

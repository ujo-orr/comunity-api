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
    // 3. 전화번호 기준 회원 조회 API
    @GetMapping("/phoneNumber/{phoneNumber}")
    public ResponseEntity<MemberResponse> getMemberInfoByPhoneNumber(@PathVariable String phoneNumber) {
        MemberResponse response = memberService.getMemberInfoByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(response);
    }

    // 이메일 기준 회원 정보 수정 API
    @PutMapping("/email/{email}")
    public ResponseEntity<Void> updateMemberByEmail(
            @PathVariable String email,
            @RequestBody MemberUpdateRequest request) {

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


        // 로그인 (토큰 반환)
        @PostMapping("/login")
        public ResponseEntity<String> login(@RequestBody MemberLoginRequest request) {
            String token = memberService.login(request);
            return ResponseEntity.ok(token);
        }

        // 본인 회원 탈퇴 (URL에 이메일 없이, 헤더의 토큰 정보 활용)
        @PostMapping("/withdraw")
        public ResponseEntity<Void> withdrawMember(
                Authentication authentication, // Spring Security의 Authentication 객체를 직접 주입받음
                @RequestBody MemberWithdrawalRequest request) {

            // authentication.getName()을 하면 Principal로 저장했던 email 문자열이 자동으로 반환
            String email = authentication.getName();

            memberService.withdrawMember(email, request);
            return ResponseEntity.ok().build();
        }

}

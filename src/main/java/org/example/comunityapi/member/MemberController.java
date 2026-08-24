package org.example.comunityapi.member;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
package org.example.comunityapi.member;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // 회원 탈퇴 API
    @DeleteMapping("/email/{email}")
    public ResponseEntity<Void> deleteMemberByEmail(
            @PathVariable String email,
            @RequestBody MemberWithdrawalRequest request) {

        memberService.withdrawMember(email, request);
        return ResponseEntity.ok().build();
    }
}
package org.example.communityapi.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.member.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(
            @Valid @RequestBody MemberSignUpRequest request) {
        Long memberId = memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberId);
    }

    @GetMapping("/search")
    public ResponseEntity<MemberSearchResponse> getMemberProfile(
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(max = 10, message = "닉네임은 10자 이하로 입력해주세요.")
            @RequestParam String nickname) {
        return ResponseEntity.ok(memberService.getMemberInfoByNickname(nickname));
    }

    @GetMapping("/me")
    public ResponseEntity<MemberMyProfileResponse> getMyInfo(
            Authentication authentication
    ) {
        String email = authentication.getName();
        MemberMyProfileResponse response = memberService.getMyProfileByEmail(email);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        memberService.updateMyProfile(authentication.getName(), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdrawMember(
            Authentication authentication,
            @Valid
            @RequestBody MemberWithdrawalRequest request) {

        String email = authentication.getName();

        memberService.withdrawMember(email, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/restore")
    public ResponseEntity<Void> cancelWithdrawal(
            @Email(message = "올바른 이메일 형식을 입력해주세요.")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Size(max = 30, message = "이메일은 30자 이하로 입력해주세요.")
            @RequestParam String email,
            @Valid @RequestBody MemberWithdrawalRequest request
    ) {
        memberService.cancelWithdrawal(email, request);
        return ResponseEntity.ok().build();
    }
}

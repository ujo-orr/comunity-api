package org.example.communityapi.member.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.member.MemberService;
import org.example.communityapi.member.admin.dto.AdminMemberResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMemberController {
    private final AdminMemberService adminMemberService;
    private final MemberService memberService;

    // 전체 회원 조회 (관리자 전용 DTO 리스트 반환)
    @GetMapping("/members")
    public ResponseEntity<List<AdminMemberResponse>> getAllMembers() {
        return ResponseEntity.ok(adminMemberService.findAllMembers());
    }

    // 회원 검색 (id, 이메일, 닉네임, 전화번호 통합 검색)
    @GetMapping("/search")
    public ResponseEntity<List<AdminMemberResponse>> searchMembers(
            @NotBlank(message = "검색어를 입력해 주세요.")
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(adminMemberService.searchMembers(keyword));
    }

    // 회원 탈퇴 수동 복구
    @PostMapping("/restore/{id}")
    public ResponseEntity<Void> cancelWithdrawal(
            @PathVariable Long id
    ) {
        adminMemberService.cancelWithdrawal(id);
        return ResponseEntity.ok().build();
    }

    // 회원 차단
    @PatchMapping("/ban/{id}")
    public ResponseEntity<Void> banMember(
            @NotBlank(message = "id를 입력해 주세요")
            @PathVariable Long id) {
        adminMemberService.banMember(id);
        return ResponseEntity.ok().build();
    }

    // 차단 해제
    @PatchMapping("/unban/{id}")
    public ResponseEntity<Void> unbanMember(
            @NotBlank(message = "id를 입력해 주세요")
            @PathVariable Long id) {
        adminMemberService.unbanMember(id);
        return ResponseEntity.ok().build();
    }


}
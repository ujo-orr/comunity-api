package org.example.communityapi.member.admin;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.member.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {
    private final AdminMemberService adminMemberService;
    private final MemberService memberService;

    // 전체 회원 조회 (관리자 전용 DTO 리스트 반환)
    @GetMapping
    public ResponseEntity<List<AdminMemberResponse>> getAllMembers() {
        return ResponseEntity.ok(adminMemberService.findAllMembers());
    }

    // 회원 검색 (id, 이메일, 닉네임, 전화번호 통합 검색)
    @GetMapping("/search")
    public ResponseEntity<List<AdminMemberResponse>> searchMembers(
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(adminMemberService.searchMembers(keyword));
    }

    // 회원 탈퇴 수동 복구
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> cancelWithdrawal(
            @PathVariable Long id
    ) {
        adminMemberService.cancelWithdrawal(id);
        return ResponseEntity.ok().build();
    }

    // 회원 차단
    @PatchMapping("/{id}/ban")
    public ResponseEntity<Void> banMember(@PathVariable Long id) {
        adminMemberService.banMember(id);
        return ResponseEntity.ok().build();
    }

    // 차단 해제
    @PatchMapping("/{id}/unban")
    public ResponseEntity<Void> unbanMember(@PathVariable Long id) {
        adminMemberService.unbanMember(id);
        return ResponseEntity.ok().build();
    }
}
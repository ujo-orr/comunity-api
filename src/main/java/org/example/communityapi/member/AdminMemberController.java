package org.example.communityapi.member;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberController {
    private final AdminMemberService adminMemberService;

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
}
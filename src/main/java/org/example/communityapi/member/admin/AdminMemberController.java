package org.example.communityapi.member.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.member.admin.dto.AdminMemberResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMemberController {
    private final AdminMemberService adminMemberService;

    @GetMapping("/members")
    public ResponseEntity<List<AdminMemberResponse>> getAllMembers() {
        return ResponseEntity.ok(adminMemberService.findAllMembers());
    }

    @GetMapping("/search")
    public ResponseEntity<List<AdminMemberResponse>> searchMembers(
            @NotBlank(message = "검색어를 입력해 주세요.")
            @Size(max = 100, message = "검색어는 100자 이하로 입력해주세요.")
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(adminMemberService.searchMembers(keyword));
    }

    @PostMapping("/restore/{id}")
    public ResponseEntity<Void> cancelWithdrawal(
            @Positive @PathVariable Long id
    ) {
        adminMemberService.cancelWithdrawal(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/ban/{id}")
    public ResponseEntity<Void> banMember(
            @Positive @PathVariable Long id) {
        adminMemberService.banMember(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/unban/{id}")
    public ResponseEntity<Void> unbanMember(
            @Positive @PathVariable Long id) {
        adminMemberService.unbanMember(id);
        return ResponseEntity.ok().build();
    }

}

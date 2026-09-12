package org.example.communityapi.member.superAdmin;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.MemberStatus;
import org.example.communityapi.member.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperAdminInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // ADMIN 권한을 가진 계정이 없으면 자동 생성
        if (!memberRepository.existsByRole(Role.ADMIN)) {
            Member superAdmin = Member.builder()
                    .email("superAdmin@system.com")
                    .password(passwordEncoder.encode("!Q2w3e4r"))
                    .nickname("SuperAdmin")
                    .phoneNumber("01000000000")
                    .role(Role.SUPERADMIN)
                    .status(MemberStatus.ACTIVE)
                    .build();
            memberRepository.save(superAdmin);
        }
    }
}
package org.example.communityapi.member.admin;

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
public class AdminInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // ADMIN 권한을 가진 계정이 없으면 자동 생성
        if (!memberRepository.existsByRole(Role.ADMIN)) {
            Member superAdmin = Member.builder()
                    .email("admin@system.com")
                    .password(passwordEncoder.encode("!Q2w3e4r"))
                    .nickname("Admin")
                    .phoneNumber("01000000001")
                    .role(Role.ADMIN)
                    .status(MemberStatus.ACTIVE)
                    .build();
            memberRepository.save(superAdmin);
        }
    }
}


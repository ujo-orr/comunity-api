package org.example.communityapi.member.admin;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.MemberStatus;
import org.example.communityapi.member.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bootstrap.admin.enabled", havingValue = "true")
public class AdminInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${BOOTSTRAP_ADMIN_PASSWORD}")
    private String bootstrapPassword;

    @Override
    public void run(String... args) {
        if (bootstrapPassword.length() < 12) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters long");
        }
        if (!memberRepository.existsByRole(Role.ADMIN)) {
            Member superAdmin = Member.builder()
                    .email("admin@system.com")
                    .password(passwordEncoder.encode(bootstrapPassword))
                    .nickname("Admin")
                    .phoneNumber("01000000001")
                    .role(Role.ADMIN)
                    .status(MemberStatus.ACTIVE)
                    .build();
            memberRepository.save(superAdmin);
        }
    }
}

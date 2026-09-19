package org.example.communityapi.member.superAdmin;

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
@ConditionalOnProperty(name = "bootstrap.superadmin.enabled", havingValue = "true")
public class SuperAdminInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${BOOTSTRAP_SUPERADMIN_PASSWORD}")
    private String bootstrapPassword;

    @Override
    public void run(String... args) {
        if (bootstrapPassword.length() < 12) {
            throw new IllegalStateException("BOOTSTRAP_SUPERADMIN_PASSWORD must be at least 12 characters long");
        }
        if (!memberRepository.existsByRole(Role.SUPERADMIN)) {
            Member superAdmin = Member.builder()
                    .email("superAdmin@system.com")
                    .password(passwordEncoder.encode(bootstrapPassword))
                    .nickname("SuperAdmin")
                    .phoneNumber("01000000000")
                    .role(Role.SUPERADMIN)
                    .status(MemberStatus.ACTIVE)
                    .build();
            memberRepository.save(superAdmin);
        }
    }
}

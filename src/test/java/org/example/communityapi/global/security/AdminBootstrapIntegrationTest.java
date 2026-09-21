package org.example.communityapi.global.security;

import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.batch.job.enabled=false",
        "bootstrap.admin.enabled=true",
        "bootstrap.superadmin.enabled=true",
        "BOOTSTRAP_ADMIN_PASSWORD=test-admin-password-123",
        "BOOTSTRAP_SUPERADMIN_PASSWORD=test-superadmin-password-456"
})
class AdminBootstrapIntegrationTest {
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("관리자와 최고 관리자는 각자 설정된 비밀번호와 권한으로 생성된다")
    void createsAdministratorsWithSeparateConfiguredPasswords() {
        var admin = memberRepository.findByEmail("admin@system.com").orElseThrow();
        var superAdmin = memberRepository.findByEmail("superAdmin@system.com").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(superAdmin.getRole()).isEqualTo(Role.SUPERADMIN);
        assertThat(passwordEncoder.matches("test-admin-password-123", admin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("test-superadmin-password-456", superAdmin.getPassword())).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo(superAdmin.getPassword());
    }
}

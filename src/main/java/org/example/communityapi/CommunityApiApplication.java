package org.example.communityapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// 자체 JWT 인증 사용에 따른 기본 사용자 자동설정 제외
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling

public class CommunityApiApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(CommunityApiApplication.class, args);
    }

}

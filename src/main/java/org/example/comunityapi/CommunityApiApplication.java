package org.example.comunityapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing

public class CommunityApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityApiApplication.class, args);
    }

}

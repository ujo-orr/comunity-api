package org.example.comunityapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing

public class ComunityApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComunityApiApplication.class, args);
    }

}

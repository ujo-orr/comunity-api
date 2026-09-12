package org.example.communityapi.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String token;

    public RefreshToken(String email, String token) {
        this.email = email;
        this.token = token;
    }

    // Refresh Token 값 갱신 메서드
    public void updateToken(String newToken) {
        this.token = newToken;
    }
}

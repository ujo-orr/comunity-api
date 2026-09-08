package org.example.communityapi.member;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // USER
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByPhoneNumber(String phoneNumber);

    Optional<Member> findByEmail(String email);
    Optional<Member> findByNickname(String nickname);

    // ADMIN
    boolean existsByRole(Role role);

    @Query("SELECT m FROM Member m WHERE " +
            "CAST(m.id AS string) LIKE %:keyword% OR " +
            "m.email LIKE %:keyword% OR " +
            "m.nickname LIKE %:keyword% OR " +
            "m.phoneNumber LIKE %:keyword%")
    List<Member> searchByKeyword(@Param("keyword") String keyword);
}

package org.example.communityapi.member;

import org.example.communityapi.member.admin.dto.AdminMemberResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByRole(Role role);

    Optional<Member> findByEmail(String email);
    Optional<Member> findByNickname(String nickname);

    @Query("""
            SELECT new org.example.communityapi.member.admin.dto.AdminMemberResponse(
                m.id, m.email, m.nickname, m.phoneNumber, m.role, m.status, m.createdAt, m.updatedAt
            )
            FROM Member m
            ORDER BY m.createdAt DESC
            """)
    List<AdminMemberResponse> findAllAdminMemberResponses();

    @Query("SELECT new org.example.communityapi.member.admin.dto.AdminMemberResponse(" +
            "m.id, m.email, m.nickname, m.phoneNumber, m.role, m.status, m.createdAt, m.updatedAt) " +
            "FROM Member m WHERE " +
            "CAST(m.id AS string) LIKE %:keyword% OR " +
            "m.email LIKE %:keyword% OR " +
            "m.nickname LIKE %:keyword% OR " +
            "m.phoneNumber LIKE %:keyword% " +
            "ORDER BY m.createdAt DESC")
    List<AdminMemberResponse> searchAdminMemberResponsesByKeyword(@Param("keyword") String keyword);
}

package org.example.comunityapi.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    // 회원가입 비즈니스 로직
    @Transactional
    public Long signUp(MemberSignUpRequest request) {

        // 1. 이메일 중복 체크
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 2. 닉네임 중복 체크
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        // 3. 전화번호 중복 체크
        if (memberRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("이미 등록된 전화번호입니다.");
        }

        // 4. 비밀번호 암호화 (추후 Spring Security 적용 시 암호화 로직 추가 예정)
        String encodedPassword = request.getPassword();

        // 5. DTO를 엔티티로 변환 후 DB에 저장
        Member member = request.toEntity(encodedPassword);
        Member savedMember = memberRepository.save(member);

        // 6. 생성된 회원의 ID(PK) 반환
        return savedMember.getId();
    }
}
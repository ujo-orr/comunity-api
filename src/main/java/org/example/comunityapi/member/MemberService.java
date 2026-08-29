package org.example.comunityapi.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class MemberService {
    private final MemberRepository memberRepository;

    // 회원가입
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
        // 5. DTO를 엔터티로 변환 후 DB에 저장
        Member member = request.toEntity(encodedPassword);
        Member savedMember = memberRepository.save(member);
        // 6. 생성된 회원의 ID(PK) 반환
        return savedMember.getId();
    }

    // 조회
    public MemberResponse getMemberInfo(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(id + "은(는) 존재하지 않는 회원 입니다."));
        return new MemberResponse(member);
    }
    public MemberResponse getMemberInfoByNickname(String nickname) {
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException(nickname + "은(는) 존재하지 않는 닉네임 입니다."));
        return new MemberResponse(member);
    }
    public MemberResponse getMemberInfoByPhoneNumber(String phoneNumber) {
        Member member = memberRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException(phoneNumber + "은(는) 존재하지 않는 전화번호 입니다."));
        return new MemberResponse(member);
    }
    public MemberResponse getMemberInfoByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(email + "은(는) 존재하지 않는 전화번호 입니다."));
        return new MemberResponse(member);
    }

    // 수정
    @Transactional
    public void updateMemberByEmail(String email, MemberUpdateRequest request) {
        // 1. 이메일로 수정할 대상 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));
        // 2. 닉네임 변경 시 중복 검사
        if (!member.getNickname().equals(request.getNickname())
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        // 3. 전화번호 변경 시 중복 검사
        if (!member.getPhoneNumber().equals(request.getPhoneNumber())
                && memberRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("이미 등록된 전화번호입니다.");
        }
        // 4. 비밀번호 처리 및 상태 변경 (Dirty Checking)
        String encodedPassword = request.getPassword();
        member.updateProfile(request.getNickname(), request.getPhoneNumber(), encodedPassword);
    }

    /*
    @Transactional
    public void deleteMemberByEmail(String email, MemberDeleteRequest request) {
        // 1. 삭제할 대상 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(email + "은(는) 존재하지 않는 회원입니다."));
        // 2. 비밀번호 일치 여부 확인 (추후 Security 적용 시 passwordEncoder.matches 사용)
        if (!member.getPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        // 3. 회원 DB 삭제
        memberRepository.delete(member); // 또는 memberRepository.deleteById(member.getId());
    }
     */

    @Transactional
    public void deleteMemberByEmail(String email, MemberDeleteRequest request) {
        // 1. 삭제되지 않은 회원 조회
        Member member = memberRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));

        // 2. 비밀번호 확인
        if (!member.getPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. Soft Delete 처리 (JPA Dirty Checking으로 UPDATE 쿼리 발생)
        // 실제 DB에는 DELETE 쿼리가 아닌 'UPDATE member SET deleted = true ...' 쿼리가 나갑니다.
        member.deleteAccount();
    }
}
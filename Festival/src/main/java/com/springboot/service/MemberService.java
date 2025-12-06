package com.springboot.service;

import com.springboot.domain.Member;
import com.springboot.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 생성자 주입
    public MemberService(MemberRepository memberRepository,
                         PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 이메일로 회원 조회
    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    // 이메일 중복 여부 확인
    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    // 그냥 엔티티 저장 (필요하면 계속 사용)
    @Transactional
    public Member save(Member member) {
        return memberRepository.save(member);
    }

    // 회원가입용: raw password 받아서 암호화 + 저장
    @Transactional
    public Member createMember(String name, String email, String rawPassword) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(rawPassword));
        return memberRepository.save(member);
    }

    // Principal에서 현재 로그인 회원 조회
    public Member getCurrentMember(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("로그인 정보가 없습니다.");
        }

        String email = principal.getName();
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    // 비밀번호 확인 (현재 비밀번호 체크용)
    public boolean checkPassword(String rawPassword, Member member) {
        return passwordEncoder.matches(rawPassword, member.getPassword());
    }

    // 비밀번호 변경
    @Transactional
    public void changePassword(Member member, String newRawPassword) {
        String encoded = passwordEncoder.encode(newRawPassword);
        member.setPassword(encoded);
        memberRepository.save(member);
    }

    // 이름 변경 같은 간단한 프로필 수정
    @Transactional
    public void updateName(Member member, String newName) {
        member.setName(newName);
        memberRepository.save(member);
    }
}
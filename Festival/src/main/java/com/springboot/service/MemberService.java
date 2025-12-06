package com.springboot.service;

import com.springboot.domain.Member;
import com.springboot.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    // 생성자 주입
    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // 이메일로 회원 조회
    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    // 이메일 중복 여부 확인
    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    // 회원 저장(회원가입 등)
    public Member save(Member member) {
        return memberRepository.save(member);
    }
}

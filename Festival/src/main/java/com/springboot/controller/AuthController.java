package com.springboot.controller;

import com.springboot.domain.Member;
import com.springboot.repository.MemberRepository;
import com.springboot.dto.SignupForm;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(MemberRepository memberRepository,
                          PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 로그인 폼
    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    // 회원가입 폼
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "signup";
    }

    // 회원가입 처리
    @PostMapping("/signup")
    public String signupSubmit(@Valid @ModelAttribute("signupForm") SignupForm form,
                               BindingResult bindingResult,
                               Model model) {

        // 1) 기본 검증 에러 있으면 그대로 폼으로
        if (bindingResult.hasErrors()) {
            return "signup";
        }

        // 2) 비밀번호 확인 일치 검사
        if (!form.getPassword().equals(form.getPasswordConfirm())) {
            bindingResult.rejectValue(
                    "passwordConfirm",
                    "password.mismatch",
                    "비밀번호가 일치하지 않습니다."
            );
            return "signup";
        }

        // 3) 이메일 중복 체크
        if (memberRepository.existsByEmail(form.getEmail())) {
            bindingResult.rejectValue(
                    "email",
                    "email.duplicate",
                    "이미 가입된 이메일입니다."
            );
            return "signup";
        }

        // 4) Member 엔티티 생성 + 비밀번호 암호화
        Member member = new Member();
        member.setName(form.getName());
        member.setEmail(form.getEmail());
        member.setPassword(passwordEncoder.encode(form.getPassword()));

        memberRepository.save(member);

        // 5) 가입 후 로그인 페이지로 리다이렉트
        return "redirect:/auth/login?signupSuccess";
    }
}

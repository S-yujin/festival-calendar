package com.springboot.controller;

import com.springboot.domain.Member;
import com.springboot.dto.ChangePasswordForm;
import com.springboot.repository.MemberRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
@RequestMapping("/members")
public class MemberController {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberController(MemberRepository memberRepository,
                            PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 이미 있던 마이페이지
    @GetMapping("/me")
    public String myPage(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/auth/login";
        }

        String email = principal.getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        model.addAttribute("member", member);
        return "mypage";
    }

    // 비밀번호 변경 폼 (GET)
    @GetMapping("/password")
    public String changePasswordForm(Model model) {
        model.addAttribute("form", new ChangePasswordForm());
        return "change-password";
    }

    // 비밀번호 변경 처리 (POST)
    @PostMapping("/password")
    public String changePasswordSubmit(@Valid @ModelAttribute("form") ChangePasswordForm form,
                                       BindingResult bindingResult,
                                       Principal principal,
                                       Model model) {

        if (principal == null) {
            return "redirect:/auth/login";
        }

        String email = principal.getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        // 1) 기본 검증 에러
        if (bindingResult.hasErrors()) {
            return "change-password";
        }

        // 2) 현재 비밀번호 일치 확인
        if (!passwordEncoder.matches(form.getCurrentPassword(), member.getPassword())) {
            bindingResult.rejectValue(
                    "currentPassword",
                    "password.current.invalid",
                    "현재 비밀번호가 일치하지 않습니다."
            );
            return "change-password";
        }

        // 3) 새 비밀번호 / 확인 일치 확인
        if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            bindingResult.rejectValue(
                    "newPasswordConfirm",
                    "password.new.mismatch",
                    "새 비밀번호가 서로 일치하지 않습니다."
            );
            return "change-password";
        }

        // 4) 비밀번호 실제 변경
        String encoded = passwordEncoder.encode(form.getNewPassword());
        member.setPassword(encoded);
        memberRepository.save(member);

        // 성공 후 마이페이지로
        return "redirect:/members/me?passwordChanged";
    }
}

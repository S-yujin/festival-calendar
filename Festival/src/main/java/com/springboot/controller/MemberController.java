package com.springboot.controller;

import com.springboot.domain.Member;
import com.springboot.dto.ChangePasswordForm;
import com.springboot.dto.EditProfileForm;
import com.springboot.service.MemberService;
import com.springboot.dto.BookmarkResponse;
import com.springboot.service.BookmarkService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final BookmarkService bookmarkService;  // ✅ 추가

    // ✅ 생성자에 BookmarkService 추가
    public MemberController(MemberService memberService, BookmarkService bookmarkService) {
        this.memberService = memberService;
        this.bookmarkService = bookmarkService;
    }

    // 마이페이지
    @GetMapping("/mypage")
    public String mypage(Principal principal, Model model) {
        Member member = memberService.getCurrentMember(principal);
        
        // 북마크 목록 조회
        List<BookmarkResponse> bookmarks = bookmarkService.getBookmarks(member)
                .stream()
                .map(BookmarkResponse::from)
                .collect(Collectors.toList());

        model.addAttribute("member", member);
        model.addAttribute("bookmarks", bookmarks);
        model.addAttribute("bookmarkCount", bookmarks.size());
        
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

        Member member = memberService.getCurrentMember(principal);

        // 1) 기본 검증 에러
        if (bindingResult.hasErrors()) {
            return "change-password";
        }

        // 2) 현재 비밀번호 일치 확인
        if (!memberService.checkPassword(form.getCurrentPassword(), member)) {
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

        // 4) 비밀번호 실제 변경 (서비스에서 암호화 포함)
        memberService.changePassword(member, form.getNewPassword());

        // 성공 후 마이페이지로
        return "redirect:/members/mypage?passwordChanged";
    }
    
    // 프로필 수정 폼
    @GetMapping("/edit")
    public String editProfileForm(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/auth/login";
        }

        String email = principal.getName();
        Member member = memberService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        EditProfileForm form = new EditProfileForm();
        form.setName(member.getName());
        form.setEmail(member.getEmail());

        model.addAttribute("form", form);
        return "edit-profile";
    }
    
    // 프로필 수정 처리
    @PostMapping("/edit")
    public String editProfileSubmit(@Valid @ModelAttribute("form") EditProfileForm form,
                                    BindingResult bindingResult,
                                    Principal principal,
                                    Model model) {

        if (principal == null) {
            return "redirect:/auth/login";
        }

        Member member = memberService.getCurrentMember(principal);

        if (bindingResult.hasErrors()) {
            return "edit-profile";
        }

        // 이름 변경
        memberService.updateName(member, form.getName());

        // 수정 후 마이페이지로 리다이렉트
        return "redirect:/members/mypage?profileUpdated";
    }

    // 회원 탈퇴 처리
    @PostMapping("/delete")
    public String deleteMember(Principal principal,
                               HttpServletRequest request,
                               HttpServletResponse response) {

        if (principal == null) {
            return "redirect:/auth/login";
        }

        Member member = memberService.getCurrentMember(principal);

        // 1) DB에서 회원 삭제
        memberService.delete(member);

        // 2) 시큐리티 세션/쿠키 로그아웃 처리
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }

        // 3) 메인 페이지로 리다이렉트 + 쿼리 파라미터로 표시
        return "redirect:/festivals";
    }
}
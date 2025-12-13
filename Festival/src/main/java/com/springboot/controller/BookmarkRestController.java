package com.springboot.controller;

import com.springboot.domain.Member;
import com.springboot.service.BookmarkService;
import com.springboot.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkRestController {

    private final BookmarkService bookmarkService;
    private final MemberService memberService;

    // 북마크 추가
    @PostMapping("/event/{eventId}")
    public ResponseEntity<?> addBookmark(
            @PathVariable("eventId") Long eventId,
            Principal principal
    ) {
        try {
            Member member = memberService.getCurrentMember(principal);
            bookmarkService.addBookmark(member, eventId);
            return ResponseEntity.ok(Map.of("message", "북마크가 추가되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "북마크 추가에 실패했습니다."));
        }
    }

    // 북마크 제거 (축제 ID로)
    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<?> removeBookmark(
            @PathVariable("eventId") Long eventId,
            Principal principal
    ) {
        try {
            Member member = memberService.getCurrentMember(principal);
            bookmarkService.removeBookmark(member, eventId);
            return ResponseEntity.ok(Map.of("message", "북마크가 제거되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "북마크 제거에 실패했습니다."));
        }
    }

    // 북마크 제거 (북마크 ID로)
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<?> removeBookmarkById(
            @PathVariable("bookmarkId") Long bookmarkId,   // ✅ 여기만 수정
            Principal principal
    ) {
        try {
            Member member = memberService.getCurrentMember(principal);
            bookmarkService.removeBookmarkById(member, bookmarkId);
            return ResponseEntity.ok(Map.of("message", "북마크가 제거되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "북마크 제거에 실패했습니다."));
        }
    }

    // 북마크 여부 확인
    @GetMapping("/event/{eventId}/check")
    public ResponseEntity<?> checkBookmark(
            @PathVariable("eventId") Long eventId,
            Principal principal
    ) {
        try {
            Member member = memberService.getCurrentMember(principal);
            boolean isBookmarked = bookmarkService.isBookmarked(member, eventId);
            return ResponseEntity.ok(Map.of("isBookmarked", isBookmarked));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("isBookmarked", false));
        }
    }
}

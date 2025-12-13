package com.springboot.service;

import com.springboot.domain.Bookmark;
import com.springboot.domain.FestivalEvent;
import com.springboot.domain.Member;
import com.springboot.repository.BookmarkRepository;
import com.springboot.repository.FestivalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final FestivalEventRepository eventRepository;

    // 북마크 추가
    @Transactional
    public Bookmark addBookmark(Member member, Long eventId) {
        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("축제를 찾을 수 없습니다."));

        // 이미 북마크가 있는지 확인
        if (bookmarkRepository.existsByMemberAndEvent(member, event)) {
            throw new IllegalStateException("이미 북마크된 축제입니다.");
        }

        Bookmark bookmark = new Bookmark();
        bookmark.setMember(member);
        bookmark.setEvent(event);

        return bookmarkRepository.save(bookmark);
    }

    // 북마크 제거
    @Transactional
    public void removeBookmark(Member member, Long eventId) {
        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("축제를 찾을 수 없습니다."));

        Bookmark bookmark = bookmarkRepository.findByMemberAndEvent(member, event)
                .orElseThrow(() -> new IllegalArgumentException("북마크를 찾을 수 없습니다."));

        bookmarkRepository.delete(bookmark);
    }

    // 북마크 ID로 제거
    @Transactional
    public void removeBookmarkById(Member member, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new IllegalArgumentException("북마크를 찾을 수 없습니다."));

        // 본인의 북마크인지 확인
        if (!bookmark.getMember().getId().equals(member.getId())) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }

        bookmarkRepository.delete(bookmark);
    }

    // 북마크 여부 확인
    public boolean isBookmarked(Member member, Long eventId) {
        FestivalEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return false;
        }
        return bookmarkRepository.existsByMemberAndEvent(member, event);
    }

    // 회원의 모든 북마크 조회
    public List<Bookmark> getBookmarks(Member member) {
        return bookmarkRepository.findByMemberOrderByCreatedAtDesc(member);
    }

    // 북마크 개수 조회
    public long getBookmarkCount(Member member) {
        return bookmarkRepository.countByMember(member);
    }
}
package com.springboot.repository;

import com.springboot.domain.Bookmark;
import com.springboot.domain.FestivalEvent;
import com.springboot.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    
    // 특정 회원의 특정 축제 북마크 찾기
    Optional<Bookmark> findByMemberAndEvent(Member member, FestivalEvent event);
    
    // 특정 회원의 북마크 존재 여부 확인
    boolean existsByMemberAndEvent(Member member, FestivalEvent event);
    
    // 특정 회원의 모든 북마크 조회 (최신순)
    List<Bookmark> findByMemberOrderByCreatedAtDesc(Member member);
    
    // 특정 회원의 북마크 개수
    long countByMember(Member member);
}
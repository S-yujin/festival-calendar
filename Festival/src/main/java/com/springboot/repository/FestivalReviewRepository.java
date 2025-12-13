package com.springboot.repository;

import com.springboot.domain.FestivalReview;
import com.springboot.domain.FestivalEvent;
import com.springboot.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalReviewRepository extends JpaRepository<FestivalReview, Long> {

    // FestivalEvent 기준으로 리뷰 조회
    List<FestivalReview> findByEventOrderByCreatedAtDesc(FestivalEvent event);
    
    // Member 기준으로 리뷰 조회
    List<FestivalReview> findByMemberOrderByCreatedAtDesc(Member member);
    
    // Event와 Member로 리뷰 존재 여부 확인
    boolean existsByEventAndMember(FestivalEvent event, Member member);
}
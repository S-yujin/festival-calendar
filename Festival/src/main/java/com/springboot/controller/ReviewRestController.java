package com.springboot.controller;

import com.springboot.domain.FestivalReview;
import com.springboot.domain.Member;
import com.springboot.repository.FestivalReviewRepository;
import com.springboot.repository.MemberRepository;
import com.springboot.dto.ReviewForm;
import com.springboot.dto.ReviewResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.security.Principal;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewRestController {

    private final FestivalReviewRepository reviewRepository;
    private final MemberRepository memberRepository;
    
    //현제 로그인 한 회원 찾기
    private Member getCurrentMember(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        return memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다."
                ));
    }
    
    // 현재 로그인한 회원 인지 검증
    private void checkOwner(FestivalReview review, Member currentMember) {
        if (review.getMember() == null ||
            !review.getMember().getId().equals(currentMember.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 작성한 리뷰만 수정/삭제할 수 있습니다."
            );
        }
    }
   
     // 리뷰 수정 (내용/별점 일부만 수정)
    @PatchMapping("/{id}")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable("id") Long id,
            @Valid @RequestBody ReviewForm form
    ) {
        FestivalReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다. id=" + id));

        review.setContent(form.getContent());
        review.setRating(form.getRating());
        review.setUpdatedAt(LocalDateTime.now());

        FestivalReview saved = reviewRepository.save(review);
        return ResponseEntity.ok(ReviewResponse.from(saved));
    }

 
    // 리뷰 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable("id") Long id) {
        if (!reviewRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "리뷰를 찾을 수 없습니다. id=" + id
            );
        }
        reviewRepository.deleteById(id);
        log.info("리뷰 삭제 완료: id={}", id);

        return ResponseEntity.noContent().build();
    }
}

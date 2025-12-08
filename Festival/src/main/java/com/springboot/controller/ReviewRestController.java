package com.springboot.controller;

import com.springboot.domain.FestivalReview;
import com.springboot.repository.FestivalReviewRepository;
import com.springboot.dto.ReviewForm;
import com.springboot.dto.ReviewResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewRestController {

    private final FestivalReviewRepository reviewRepository;

   
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

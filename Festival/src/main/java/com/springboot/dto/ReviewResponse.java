package com.springboot.dto;

import com.springboot.domain.FestivalReview;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ReviewResponse {

    private Long id;
    private String nickname;
    private String content;
    private int rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long memberId;  // ✅ 추가

    public static ReviewResponse from(FestivalReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getNickname(),
                review.getContent(),
                review.getRating(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getMember() != null ? review.getMember().getId() : null  // ✅ 추가
        );
    }
}
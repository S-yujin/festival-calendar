package com.springboot.controller;

import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalReview;
import com.springboot.domain.Member;
import com.springboot.repository.FestivalEventRepository;
import com.springboot.repository.FestivalReviewRepository;
import com.springboot.repository.MemberRepository;
import com.springboot.dto.ReviewForm;
import com.springboot.dto.ReviewResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewRestController {

    private final FestivalReviewRepository reviewRepository;
    private final FestivalEventRepository eventRepository;
    private final MemberRepository memberRepository;
    
    // 특정 축제의 리뷰 목록 조회
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<ReviewResponse>> getEventReviews(@PathVariable Long eventId) {
        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "축제를 찾을 수 없습니다."));
        
        List<ReviewResponse> reviews = reviewRepository.findByEventOrderByCreatedAtDesc(event)
                .stream()
                .map(ReviewResponse::from)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(reviews);
    }
    
    // 리뷰 작성 (RequestParam 방식)
    @PostMapping(
        value = "/event/{eventId}",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable("eventId") Long eventId,
            @RequestParam("content") String content,
            @RequestParam("rating") Integer rating,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Principal principal
    ) {
        log.info("리뷰 작성 요청: eventId={}, content={}, rating={}", eventId, content, rating);
        
        Member member = getCurrentMember(principal);

        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "축제를 찾을 수 없습니다."));

        // 중복 리뷰 체크
        if (reviewRepository.existsByEventAndMember(event, member)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "이미 이 축제에 리뷰를 작성하셨습니다.");
        }

        // 유효성 검사
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "리뷰 내용을 입력해주세요.");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "평점은 1~5 사이의 값이어야 합니다.");
        }

        FestivalReview review = new FestivalReview();
        review.setEvent(event);
        review.setMember(member);
        review.setContent(content);
        review.setRating(rating);
        review.setNickname(member.getName());

        // 파일 있으면 저장 처리
        if (file != null && !file.isEmpty()) {
            log.info("파일 업로드: filename={}, size={}", file.getOriginalFilename(), file.getSize());
            // TODO: 파일 저장 로직
            // Long attachmentId = fileStorageService.save(file);
            // review.setAttachmentId(attachmentId);
        }

        FestivalReview saved = reviewRepository.save(review);
        log.info("리뷰 작성 완료: id={}, eventId={}, memberId={}",
                saved.getId(), eventId, member.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReviewResponse.from(saved));
    }

    // 현재 로그인한 회원 찾기
    private Member getCurrentMember(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        return memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다."
                ));
    }
    
    // 현재 로그인한 회원인지 검증
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
            @Valid @RequestBody ReviewForm form,
            Principal principal
    ) {
        Member member = getCurrentMember(principal);
        
        FestivalReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다. id=" + id));

        checkOwner(review, member);
        
        review.setContent(form.getContent());
        review.setRating(form.getRating());
        review.setUpdatedAt(LocalDateTime.now());

        FestivalReview saved = reviewRepository.save(review);
        log.info("리뷰 수정 완료: id={}", id);
        
        return ResponseEntity.ok(ReviewResponse.from(saved));
    }

    // 리뷰 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        Member member = getCurrentMember(principal);
        
        FestivalReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다. id=" + id));
        
        checkOwner(review, member);
        
        reviewRepository.deleteById(id);
        log.info("리뷰 삭제 완료: id={}", id);

        return ResponseEntity.noContent().build();
    }
}
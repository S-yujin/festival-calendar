package com.springboot.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "festival_review")
public class FestivalReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 축제에 대한 리뷰인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festivals festival;

    // 누가 썼는지 (Member 연결)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private String nickname;   // 화면에 보일 이름 (member.getName() 복사해두기)

    @Column(nullable = false, length = 1000)
    private String content;

    // 별점 1~5
    private int rating;

    // (옵션) 첨부 이미지 파일 id (없으면 null)
    private Long attachmentId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

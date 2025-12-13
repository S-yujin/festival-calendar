package com.springboot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "festival_attachment")
@Getter
@Setter
public class FestivalAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Festivals → FestivalEvent로 변경
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id")  // 기존 컬럼명 유지
    private FestivalEvent event;  // 필드명 변경

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "stored_filename")
    private String storedFilename;

    @Column(name = "content_type")
    private String contentType;

    private Long size;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by")
    private String uploadedBy;
}
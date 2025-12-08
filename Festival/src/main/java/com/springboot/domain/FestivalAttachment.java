package com.springboot.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "festival_attachment")
public class FestivalAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 축제에 붙은 파일인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festivals festival;

    private String originalFilename;   // 사용자가 올린 이름
    private String storedFilename;     // 서버에 저장할 실제 파일명 (UUID 등)
    private String contentType;
    private long size;

    private LocalDateTime uploadedAt;
    private String uploadedBy;         // 업로더 이름 or email
}

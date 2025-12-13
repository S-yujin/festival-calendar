package com.springboot.repository;

import com.springboot.domain.FestivalAttachment;
import com.springboot.domain.FestivalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalAttachmentRepository extends JpaRepository<FestivalAttachment, Long> {
    
    // FestivalEvent 기준으로 첨부파일 조회
    List<FestivalAttachment> findByEvent(FestivalEvent event);
    
    // FestivalEvent 기준으로 첨부파일 개수 조회
    long countByEvent(FestivalEvent event);
}
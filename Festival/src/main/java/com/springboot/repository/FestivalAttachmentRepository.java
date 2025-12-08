package com.springboot.repository;

import com.springboot.domain.FestivalAttachment;
import com.springboot.domain.Festivals;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalAttachmentRepository
        extends JpaRepository<FestivalAttachment, Long> {

    List<FestivalAttachment> findByFestivalOrderByUploadedAtDesc(Festivals festival);
}

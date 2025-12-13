package com.springboot.repository;

import com.springboot.domain.FestivalMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FestivalMasterRepository extends JpaRepository<FestivalMaster, Long> {
    
    Optional<FestivalMaster> findByTourApiContentId(Long tourApiContentId);
}
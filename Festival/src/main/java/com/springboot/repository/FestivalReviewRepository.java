package com.springboot.repository;

import com.springboot.domain.FestivalReview;
import com.springboot.domain.Festivals;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalReviewRepository extends JpaRepository<FestivalReview, Long> {

    List<FestivalReview> findByFestivalOrderByCreatedAtDesc(Festivals festival);
}

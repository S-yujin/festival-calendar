package com.springboot.repository;

import com.springboot.domain.festivales;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FestivalesRepository extends JpaRepository<festivales, Long> {
	 // 특정 날짜에 열리는 축제 (시/도 기준)
    List<festivales> findByCtprvnNmAndFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(
            String ctprvnNm, LocalDate from, LocalDate to
    );

    // 한 달 동안 열리는 축제 (캘린더용)
    List<festivales> findByFstvlBeginDeBetween(LocalDate start, LocalDate end);
}

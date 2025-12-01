package com.springboot.repository;

import com.springboot.domain.Festivals;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FestivalsRepository extends JpaRepository<Festivals, Long> {
	 // 특정 날짜에 열리는 축제 (시/도 기준)
    List<Festivals> findByCtprvnNmAndFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(
            String ctprvnNm, LocalDate from, LocalDate to
    );

    // 한 달 동안 열리는 축제 (캘린더용)
    List<Festivals> findByFstvlBeginDeBetween(LocalDate start, LocalDate end);
    
    List<Festivals> findByFcltyNmContainingIgnoreCaseOrFstvlCnContainingIgnoreCase(
    		String namekeyword,
    		String contentkeyword
    		);
}
package com.springboot.repository;

import com.springboot.domain.Festivals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FestivalsRepository extends JpaRepository<Festivals, Long> {

    // 특정 날짜에 열리는 축제 (시/도 기준)
    List<Festivals> findByCtprvnNmAndFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(
            String ctprvnNm, LocalDate from, LocalDate to
    );

    // 연도별 조회 (YEAR 함수 사용)
    @Query("select f from Festivals f where YEAR(f.fstvlBeginDe) = :year")
    List<Festivals> findByYear(@Param("year") int year);

    // 한 달 동안 열리는 축제 (캘린더용)
    List<Festivals> findByFstvlBeginDeBetween(LocalDate start, LocalDate end);

    // 검색
    List<Festivals> findByFcltyNmContainingIgnoreCaseOrFstvlCnContainingIgnoreCase(
            String namekeyword,
            String contentkeyword
    );

    // “이름 일부”로 과거 히스토리(연도 순) 조회 – 패턴 분석용
    List<Festivals> findByFcltyNmContainingOrderByFstvlBeginDeAsc(String namePart);
}
package com.springboot.repository;

import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalMaster;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FestivalEventRepository extends JpaRepository<FestivalEvent, Long> {

    // 진행 중인 축제 조회
    @Query("SELECT e FROM FestivalEvent e " +
           "JOIN FETCH e.master m " +
           "WHERE e.fstvlStart <= :today AND e.fstvlEnd >= :today " +
           "ORDER BY e.fstvlStart")
    List<FestivalEvent> findOngoing(@Param("today") LocalDate today, Pageable pageable);

    // 특정 기간에 시작하는 축제 조회
    @Query("SELECT e FROM FestivalEvent e " +
           "JOIN FETCH e.master m " +
           "WHERE e.fstvlStart BETWEEN :start AND :end " +
           "ORDER BY e.fstvlStart")
    List<FestivalEvent> findByStartBetween(@Param("start") LocalDate start, 
                                           @Param("end") LocalDate end, 
                                           Pageable pageable);

    // 기간이 겹치는 축제 조회
    @Query("SELECT e FROM FestivalEvent e " +
           "JOIN FETCH e.master m " +
           "WHERE e.fstvlEnd >= :start AND e.fstvlStart <= :end " +
           "ORDER BY e.fstvlStart")
    List<FestivalEvent> findOverlapping(@Param("start") LocalDate start, 
                                        @Param("end") LocalDate end);

    // fcltyNm으로 검색 (FestivalPatternService용)
    @Query("SELECT e FROM FestivalEvent e " +
           "JOIN FETCH e.master m " +
           "WHERE e.fcltyNm LIKE %:keyword% " +
           "ORDER BY e.fstvlStart ASC")
    List<FestivalEvent> findByFcltyNmContaining(@Param("keyword") String keyword);
    
    // Master와 날짜로 검색 (중복 체크용)
    List<FestivalEvent> findByMasterAndFstvlStartAndFstvlEnd(
        FestivalMaster master, 
        LocalDate fstvlStart, 
        LocalDate fstvlEnd
    );
}
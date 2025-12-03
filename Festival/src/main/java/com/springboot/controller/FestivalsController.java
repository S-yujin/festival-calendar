package com.springboot.controller;

import com.springboot.domain.Festivals;
import com.springboot.repository.FestivalsRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Controller
@RequestMapping("/festivals")   // 공통 prefix
public class FestivalsController {

    private final FestivalsRepository repository;

    public FestivalsController(FestivalsRepository repository) {
        this.repository = repository;
    }

    //전체 목록
    @GetMapping("/2024")
    public String list2024(Model model) {
        List<Festivals> list = repository.findAll();
        model.addAttribute("festivals", list);
        return "list2024";       // templates/list2024.html
    }
    
    // 연/월별 캘린더
    @GetMapping("/2024/calendar")
    public String calendar2024(
            @RequestParam(name = "year", defaultValue = "2024") int year,
            @RequestParam(name = "month", defaultValue = "12") int month,
            Model model) {

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Festivals> list = repository.findByFstvlBeginDeBetween(start, end);

        // 날짜별로 축제 묶기
        Map<LocalDate, List<Festivals>> byDate = list.stream()
                .collect(Collectors.groupingBy(Festivals::getFstvlBeginDe));

        model.addAttribute("calendarStart", start);
        model.addAttribute("calendarEnd", end);
        model.addAttribute("festivalMap", byDate);
        model.addAttribute("year", year);
        model.addAttribute("month", month);
        return "calendar2024";
    }
    
    // 상세페이지
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        Festivals festival = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 이 축제 이름으로 월별 개최 횟수 통계 조회
        List<Object[]> rows = repository.countByMonthForFestivalName(festival.getFcltyNm());

        Integer bestMonth = null;   // 가장 자주 열린 달
        long bestCount = 0L;
        long totalCount = 0L;

        for (Object[] row : rows) {
            int month = ((Number) row[0]).intValue();   // MONTH
            long cnt   = ((Number) row[1]).longValue(); // COUNT

            totalCount += cnt;

            if (bestMonth == null) {    // rows가 count desc 순서라 첫 번째가 최다
                bestMonth = month;
                bestCount = cnt;
            }
        }

        model.addAttribute("festival", festival);
        model.addAttribute("monthStats", rows);   // [월, 횟수] 리스트
        model.addAttribute("bestMonth", bestMonth);
        model.addAttribute("bestCount", bestCount);
        model.addAttribute("totalCount", totalCount);

        return "detail";   // detail.html
    }
    
    // 검색
    @GetMapping("/search")
    public String search(
            @RequestParam(name = "q") String keyword,
            Model model
    ) {
        List<Festivals> list = repository
                .findByFcltyNmContainingIgnoreCaseOrFstvlCnContainingIgnoreCase(
                        keyword, keyword);

        model.addAttribute("festivals", list);
        model.addAttribute("keyword", keyword);

        // 검색 결과도 목록 화면 재사용
        return "list2024";
    }
}

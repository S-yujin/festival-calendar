package com.springboot.controller;

import com.springboot.domain.Festivals;
import com.springboot.service.FestivalPatternService;
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
	private final FestivalPatternService patternService;

    public FestivalsController(FestivalsRepository repository,
    						   FestivalPatternService patternService) {
        this.repository = repository;
        this.patternService = patternService;
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
    
 // 상세 페이지
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        Festivals festival = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("festival", festival);

        // 2019~2024 이력으로 2025년 예상 개최 시기 문장 생성
        String expectedPeriod = patternService.estimateExpectedPeriod2025(festival.getFcltyNm());
        model.addAttribute("expectedPeriod2025", expectedPeriod);

        return "detail";   // templates/detail.html
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

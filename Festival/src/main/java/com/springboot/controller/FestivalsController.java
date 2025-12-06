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
    
    // 메인 페이지
    @GetMapping
    public String festivalsHome(Model model) {
    	// 오늘 날짜 기준
    	LocalDate today = LocalDate.now();
    	
    	// 오늘 진행 중인 축제 전체 조회
    	List<Festivals> ongoingAll = repository
    			.findByFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(today, today);
    	
    	//메인에는 4개만 맛보기
    	 List<Festivals> ongoingPreview = ongoingAll.stream()
    	            .limit(4)
    	            .collect(Collectors.toList());
    	 
        model.addAttribute("today", today);
        model.addAttribute("ongoingFestivals", ongoingPreview);

        return "festivals-home";   // festivals-home.html
    }

    // 올해(현재 연도) 전체 목록
    @GetMapping("/list")
    public String listCurrentYear(Model model) {

        int year = LocalDate.now().getYear();        // 지금 연도 (예: 2025)
        List<Festivals> list = repository.findByYear(year);

        model.addAttribute("festivals", list);
        model.addAttribute("year", year);            // 화면에서 제목에 쓰라고 같이 넘김

        return "list";
    }
   
    // 연/월별 캘린더 (현재 연도 기준)
    @GetMapping("/calendar")
    public String calendar(
            @RequestParam(name = "year", required = false) Integer yearParam,
            @RequestParam(name = "month", required = false) Integer monthParam,
            Model model) {

        int year = (yearParam != null) ? yearParam : LocalDate.now().getYear();
        int month = (monthParam != null) ? monthParam : LocalDate.now().getMonthValue();

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Festivals> list = repository.findByFstvlBeginDeBetween(start, end);

        Map<LocalDate, List<Festivals>> byDate = list.stream()
                .collect(Collectors.groupingBy(Festivals::getFstvlBeginDe));

        model.addAttribute("calendarStart", start);
        model.addAttribute("calendarEnd", end);
        model.addAttribute("festivalMap", byDate);
        model.addAttribute("year", year);
        model.addAttribute("month", month);

        return "calendar";   // calendar 로 바꾸기
    }

    
    // 상세 페이지
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        Festivals festival = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("festival", festival);

        // 예상 개최 시기 계산
        patternService.predictNextYearByName(festival.getFcltyNm())
                .ifPresent(expected -> model.addAttribute("expectedPeriod", expected));

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
        return "list";
    }
}

package com.springboot.controller;

import com.springboot.domain.Festivals;
import com.springboot.repository.FestivalsRepository;
import com.springboot.service.FestivalPatternService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        // 메인에는 4개만 맛보기
        List<Festivals> ongoingPreview = ongoingAll.stream()
                .limit(4)
                .collect(Collectors.toList());

        model.addAttribute("today", today);
        model.addAttribute("ongoingFestivals", ongoingPreview);

        return "festivals-home";   // festivals-home.html
    }

    // 리스트 기반 검색 화면
    @GetMapping("/list")
    public String listCurrentYear(
            @RequestParam(name = "region",     required = false) String region,
            @RequestParam(name = "startDate",  required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate",    required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "category",   required = false) String category,
            @RequestParam(name = "congestion", required = false) String congestion,
            Model model) {

        int year = LocalDate.now().getYear();
        List<Festivals> all = repository.findByYear(year);

        Stream<Festivals> stream = all.stream();

        // 날짜 필터
        if (startDate != null) {
            stream = stream.filter(f -> {
                LocalDate end = f.getFstvlEndDe();
                // 끝나는 날짜가 없으면 검색 결과에서 제외
                if (end == null) return false;
                return !end.isBefore(startDate);   // end >= startDate
            });
        }

        if (endDate != null) {
            stream = stream.filter(f -> {
                LocalDate begin = f.getFstvlBeginDe();
                if (begin == null) return false;
                return !begin.isAfter(endDate);    // begin <= endDate
            });
        }

        // 지역 / 유형 / 혼잡도 필터
        stream = applyContainsFilter(stream, region);
        stream = applyContainsFilter(stream, category);
        stream = applyContainsFilter(stream, congestion);

        List<Festivals> list = stream.collect(Collectors.toList());

        List<String> regions = List.of("서울", "부산", "울산", "경남", "기타");

        model.addAttribute("festivals", list);
        model.addAttribute("year", year);

        // 폼 값 유지
        model.addAttribute("region", region);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("category", category);
        model.addAttribute("congestion", congestion);
        model.addAttribute("regions", regions);

        return "list";
    }

    // 내용에 검색어가 포함되는지 간단히 체크하는 헬퍼
    private Stream<Festivals> applyContainsFilter(Stream<Festivals> stream, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return stream;
        }
        String kw = keyword.trim();
        return stream.filter(f ->
                (f.getFstvlCn() != null && f.getFstvlCn().contains(kw)) ||
                (f.getFcltyNm() != null && f.getFcltyNm().contains(kw))
        );
    }

    // 캘린더
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

        return "calendar";   // calendar.html
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

    // 키워드 검색
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
    
    // 주소에서 "서울", "부산" 같은 시/도 이름만 뽑는 헬퍼 메소드
    private String extractRegionName(Festivals f) {
    	String sido = f.getCtprvnNm();   // 시/도
        String sigungu = f.getSignguNm();
        
        if (sido == null) return null;
        if (sigungu == null || sigungu.isBlank()) return sido;
        
        // "서울특별시 강남구 ..." -> "서울특별시"
        return sido + " " + sigungu;
    }
}
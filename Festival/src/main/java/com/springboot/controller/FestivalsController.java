package com.springboot.controller;

import com.springboot.domain.Festivals;
import com.springboot.domain.FestivalReview;
import com.springboot.domain.FestivalStatus;
import com.springboot.domain.Member;
import com.springboot.dto.FestivalMarker;
import com.springboot.dto.ReviewForm;
import com.springboot.repository.FestivalsRepository;
import com.springboot.repository.FestivalReviewRepository;
import com.springboot.service.FestivalPatternService;
import com.springboot.service.MemberService;
import com.springboot.service.FileStorageService;
import com.springboot.domain.FestivalAttachment;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/festivals")   // 공통 prefix
public class FestivalsController {

    private final FestivalsRepository repository;
    private final FestivalPatternService patternService;
    private final FestivalReviewRepository reviewRepository;
    private final MemberService memberService;
    private final FileStorageService fileStorageService;
    
    public FestivalsController(FestivalsRepository repository,
                               FestivalPatternService patternService,
                               FestivalReviewRepository reviewRepository,
                               MemberService memberService,
                               FileStorageService fileStorageService) {
        this.repository = repository;
        this.patternService = patternService;
        this.reviewRepository = reviewRepository;
        this.memberService = memberService;
        this.fileStorageService = fileStorageService;
    }

    // 메인 페이지
    @GetMapping
    public String festivalMain(Model model) {
        LocalDate today = LocalDate.now();
        YearMonth nowMonth = YearMonth.now();   // import java.time.YearMonth;

        List<Festivals> ongoingFestivals =
        		repository
                        .findTop8ByFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqualOrderByFstvlBeginDeAsc(
                                today, today);

        LocalDate start = nowMonth.atDay(1);
        LocalDate end = nowMonth.atEndOfMonth();

        List<Festivals> monthlyFestivals =
        		repository
                        .findTop8ByFstvlBeginDeBetweenOrderByFstvlBeginDeAsc(start, end);

        model.addAttribute("ongoingFestivals", ongoingFestivals);
        model.addAttribute("monthlyFestivals", monthlyFestivals);
        model.addAttribute("year", nowMonth.getYear());

        return "festivals-home";   // 템플릿 이름 맞게
    }

    // 리스트 기반 검색 화면
    @GetMapping("/list")
    public String list(
            @RequestParam(name = "region",     required = false) String region,
            @RequestParam(name = "startDate",  required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate",    required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "category",   required = false) String category,
            @RequestParam(name = "congestion", required = false) String congestion,
            // ★ 달력/화면에서 보고 싶은 연도(올해/내년)를 넘길 때 사용
            @RequestParam(name = "viewYear",   required = false) Integer viewYear,
            Model model) {

        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        // ===== 1) 이번 화면에서 기준이 되는 연도 결정 =====
        int year;

        if (viewYear != null) {
            year = viewYear;                       // 달력에서 연도 넘겨 준 경우 (예: 2026)
        } else if (startDate != null) {
            year = startDate.getYear();            // 시작일로 추정
        } else if (endDate != null) {
            year = endDate.getYear();              // 종료일로 추정
        } else {
            year = currentYear;                    // 아무 정보 없으면 올해
        }

     // ===== 2) 날짜 범위 확정 (없으면 해당 year 전체) =====
        LocalDate rangeStart = (startDate != null) ? startDate : LocalDate.of(year, 1, 1);
        LocalDate rangeEnd   = (endDate   != null) ? endDate   : LocalDate.of(year, 12, 31);

        // ===== 3) DB 축제 가져오기 (레포 기준) =====
        // 레포에 있는 건 "region + 기간겹침" 메서드만 확실히 보임.
        // region 없으면 일단 findAll() 후 기간 겹침으로 걸러서 맞춤(컴파일 보장).
        List<Festivals> base;
        if (region != null && !region.isBlank()) {
            // fstvlBeginDe <= rangeEnd AND fstvlEndDe >= rangeStart  (기간 겹침)
            base = repository.findByCtprvnNmAndFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(
                    region, rangeEnd, rangeStart
            );
        } else {
            base = repository.findAll().stream()
                    .filter(f -> f.getFstvlBeginDe() != null && f.getFstvlEndDe() != null)
                    .filter(f -> !f.getFstvlBeginDe().isAfter(rangeEnd))   // begin <= rangeEnd
                    .filter(f -> !f.getFstvlEndDe().isBefore(rangeStart))  // end   >= rangeStart
                    .collect(Collectors.toList());
        }

        // ===== 4) 추가 필터 (카테고리/혼잡도 등) =====
        Stream<Festivals> stream = base.stream();

        // region은 위에서 이미 처리했으니 여기서는 안 걸어도 됨
        stream = applyContainsFilter(stream, category);
        stream = applyContainsFilter(stream, congestion);

        List<Festivals> list = stream.collect(Collectors.toList());

        // ===== 6) 진행 중 → 예정 → 지난 순 정렬 (예상 포함해서 그대로 사용) =====
        list.sort(
                Comparator.comparing((Festivals f) -> {
                    FestivalStatus status = calculateStatus(f, today);
                    return switch (status) {
                        case ONGOING  -> 0;
                        case UPCOMING -> 1;
                        case PAST     -> 2;
                    };
                }).thenComparing(Festivals::getFstvlBeginDe,
                        Comparator.nullsLast(Comparator.naturalOrder()))
        );

        // ===== 7) 지역 선택 옵션 (임시) =====
        List<String> regions = List.of("서울", "부산", "울산", "경남", "기타");

        // ===== 8) 지도용 마커 데이터 (status 포함) =====
        List<FestivalMarker> markers = list.stream()
                .filter(f -> f.getFcltyLa() != null && f.getFcltyLo() != null)
                .map(f -> {
                    FestivalStatus status = calculateStatus(f, today);
                    return new FestivalMarker(
                            f.getId(),
                            f.getFcltyNm(),
                            f.getFcltyLa(),  // 위도
                            f.getFcltyLo(),  // 경도
                            status.name()    // "ONGOING" 같은 문자열
                    );
                })
                .collect(Collectors.toList());

        // ===== 9) 모델에 담기 =====
        model.addAttribute("today", today);
        model.addAttribute("festivals", list);
        model.addAttribute("year", year);        // ★ 현재 화면 연도
        model.addAttribute("markers", markers);

        // 폼 값 유지
        model.addAttribute("region", region);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("category", category);
        model.addAttribute("congestion", congestion);
        model.addAttribute("regions", regions);

        // 내년 예측 여부를 뷰에서 쉽게 알 수 있게 플래그도 하나 던져줌
        model.addAttribute("isFutureYear", year > currentYear);

        return "list";  // list.html
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

    // 오늘 기준 축제 상태 계산
    private FestivalStatus calculateStatus(Festivals f, LocalDate today) {
        LocalDate begin = f.getFstvlBeginDe();
        LocalDate end   = f.getFstvlEndDe();

        if (begin == null || end == null) {
            return FestivalStatus.PAST; // 날짜 없으면 일단 지난 축제로 처리
        }
        if (!today.isBefore(begin) && !today.isAfter(end)) {
            return FestivalStatus.ONGOING;   // 오늘이 기간 안
        } else if (today.isBefore(begin)) {
            return FestivalStatus.UPCOMING;  // 아직 시작 전
        } else {
            return FestivalStatus.PAST;      // 이미 종료
        }
    }
    
    // 캘린더
    @GetMapping("/calendar")
    public String calendar(
            @RequestParam(name = "year", required = false) Integer yearParam,
            @RequestParam(name = "month", required = false) Integer monthParam,
            @RequestParam(name = "day", required = false) Integer dayParam,
            Model model
    ) {

        // 1) 기준 YearMonth 계산 (연/월 파라미터 없으면 오늘 기준)
        LocalDate today = LocalDate.now();
        int baseYear = (yearParam != null) ? yearParam : today.getYear();
        int baseMonth = (monthParam != null) ? monthParam : today.getMonthValue();

        // YearMonth.of 에서 0월/13월 같은 값 들어오면 예외이므로 방어코드
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.of(baseYear, baseMonth);
        } catch (Exception e) {
            yearMonth = YearMonth.from(today); // 이상한 값 들어오면 그냥 이번 달로
        }

        LocalDate monthStart = yearMonth.atDay(1);           // 해당 달의 1일
        LocalDate monthEnd = yearMonth.atEndOfMonth();       // 해당 달의 마지막 날

        // 2) 이전/다음 달 계산 (연도 넘어가는 부분도 자동 처리)
        YearMonth prevMonth = yearMonth.minusMonths(1);
        YearMonth nextMonth = yearMonth.plusMonths(1);

        // 3) 이 달과 "기간이 겹치는" 축제 전체 조회
        //    (시작일 <= monthEnd) AND (종료일 >= monthStart)
        List<Festivals> monthFestivals =
                repository.findByFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(
                        monthEnd, monthStart
                );
        // 3.5) 미래 연도면 예상 축제 합치기 (예상은 캘린더에서만)
        int baseDbYear = 2025;
        if (yearMonth.getYear() > baseDbYear) {
            List<Festivals> expected = patternService.buildExpectedFestivalsForRange(monthStart, monthEnd);

            // (선택) 중복 제거: 이름+시작일 기준
            Set<String> seen = new HashSet<>();
            for (Festivals f : monthFestivals) {
                seen.add((f.getFcltyNm() + "|" + f.getFstvlBeginDe()));
            }
            for (Festivals f : expected) {
                String key = (f.getFcltyNm() + "|" + f.getFstvlBeginDe());
                if (seen.add(key)) monthFestivals.add(f);
            }
        }

        // 4) 각 축제를 "진행 중인 날짜들"에 모두 매핑 (달력용 Map<날짜, 축제 리스트>)
        Map<LocalDate, List<Festivals>> festivalMap = new HashMap<>();

        for (Festivals f : monthFestivals) {
            LocalDate begin = f.getFstvlBeginDe();
            LocalDate end = f.getFstvlEndDe();

            if (begin == null || end == null) {
                continue; // 날짜 없으면 스킵
            }

            // 이 달 안에서 실제로 겹치는 구간만 잘라내기
            LocalDate effectiveStart = begin.isBefore(monthStart) ? monthStart : begin;
            LocalDate effectiveEnd = end.isAfter(monthEnd) ? monthEnd : end;

            for (LocalDate d = effectiveStart; !d.isAfter(effectiveEnd); d = d.plusDays(1)) {
                festivalMap
                        .computeIfAbsent(d, k -> new ArrayList<>())
                        .add(f);
            }
        }

        // 5) 날짜별 축제 리스트 정렬 (시작일, 이름 기준 등으로)
        for (Map.Entry<LocalDate, List<Festivals>> entry : festivalMap.entrySet()) {
            List<Festivals> list = entry.getValue();
            list.sort(Comparator
                    .comparing(Festivals::getFstvlBeginDe, Comparator.nullsLast(LocalDate::compareTo))
                    .thenComparing(Festivals::getFcltyNm, Comparator.nullsLast(String::compareTo))
            );
        }

        // 6) 선택된 날짜(selectedDate) 계산
        LocalDate selectedDate;
        if (dayParam != null) {
            // day 파라미터가 있으면 해당 날로 고정 (범위 넘어간 값이면 1일로)
            try {
                selectedDate = LocalDate.of(yearMonth.getYear(), yearMonth.getMonthValue(), dayParam);
            } catch (Exception e) {
                selectedDate = monthStart;
            }
        } else {
            // day 없으면
            // - 현재 달이면 today
            // - 아니면 그 달의 1일
            if (today.getYear() == yearMonth.getYear()
                    && today.getMonthValue() == yearMonth.getMonthValue()) {
                selectedDate = today;
            } else {
                selectedDate = monthStart;
            }
        }

        // 7) 선택된 날짜에 "진행 중인 축제" 리스트
        List<Festivals> dailyFestivals =
                festivalMap.getOrDefault(selectedDate, Collections.emptyList());

        // 8) 뷰로 넘길 모델 값들
        model.addAttribute("calendarStart", monthStart);
        model.addAttribute("calendarEnd", monthEnd);
        model.addAttribute("festivalMap", festivalMap);

        model.addAttribute("year", yearMonth.getYear());
        model.addAttribute("month", yearMonth.getMonthValue());

        model.addAttribute("prevYear", prevMonth.getYear());
        model.addAttribute("prevMonth", prevMonth.getMonthValue());
        model.addAttribute("nextYear", nextMonth.getYear());
        model.addAttribute("nextMonth", nextMonth.getMonthValue());

        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("dailyFestivals", dailyFestivals);

        return "calendar";
    }


    // 상세 페이지 + 리뷰 목록
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id,
                         Model model,
                         Principal principal) {

        Festivals festival = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("festival", festival);

        // 예상 개최 시기 계산
        patternService.predictNextYearByName(festival.getFcltyNm())
                .ifPresent(expected -> model.addAttribute("expectedPeriod", expected));

        // 리뷰 목록
        List<FestivalReview> reviews =
                reviewRepository.findByFestivalOrderByCreatedAtDesc(festival);
        model.addAttribute("reviews", reviews);

        // 리뷰 작성용 DTO (폼 바인딩)
        model.addAttribute("reviewForm", new ReviewForm());

        // 로그인 여부
        boolean loggedIn = (principal != null);
        model.addAttribute("loggedIn", loggedIn);
        
        // 현재 로그인한 회원 id 템플릿으로 전달
        if (loggedIn) {
            Member member = memberService.findByEmail(principal.getName())
                    .orElse(null);   // Optional<Member> 이니까

            if (member != null) {
                model.addAttribute("currentMemberId", member.getId());
            }
        }

        return "detail";   // detail.html
    }

    // 리뷰 등록 메소드
    @PostMapping("/{id}/reviews")
    public String addReview(@PathVariable("id") Long id,
                            @Valid @ModelAttribute("reviewForm") ReviewForm form,
                            BindingResult bindingResult,
                            Principal principal,
                            @RequestParam(name = "imageFile", required = false) MultipartFile imageFile
    ) throws Exception {

        if (principal == null) {
            return "redirect:/auth/login";
        }

        Festivals festival = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Member member = memberService.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        if (bindingResult.hasErrors()) {
            // 에러 처리 간단하게: 다시 상세로 리다이렉트
            return "redirect:/festivals/" + id;
        }

        FestivalReview review = new FestivalReview();
        review.setFestival(festival);
        review.setMember(member);
        review.setNickname(member.getName());
        review.setContent(form.getContent());
        review.setRating(form.getRating());
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());

        // (옵션) 이미지 첨부
        if (imageFile != null && !imageFile.isEmpty()) {
            FestivalAttachment att =
                    fileStorageService.storeFile(imageFile, festival, member.getEmail());
            review.setAttachmentId(att.getId());
        }

        reviewRepository.save(review);

        return "redirect:/festivals/" + id + "#reviews";
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

    // 주소에서 "서울", "부산" 같은 시/도 + 시군구 이름
    private String extractRegionName(Festivals f) {
        String sido = f.getCtprvnNm();   // 시/도
        String sigungu = f.getSignguNm();

        if (sido == null) return null;
        if (sigungu == null || sigungu.isBlank()) return sido;

        return sido + " " + sigungu;
    }
}

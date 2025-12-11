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
import java.util.List;
import java.util.Map;
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
    public String listCurrentYear(
            @RequestParam(name = "region",     required = false) String region,
            @RequestParam(name = "startDate",  required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate",    required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "category",   required = false) String category,
            @RequestParam(name = "congestion", required = false) String congestion,
            Model model) {

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        // 1) 해당 연도 전체 축제
        List<Festivals> all = repository.findByYear(year);
        Stream<Festivals> stream = all.stream();

        // 2) 날짜 필터
        if (startDate != null) {
            stream = stream.filter(f -> {
                LocalDate end = f.getFstvlEndDe();
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

        // 3) 지역 / 유형 / 혼잡도 필터 (지금은 간단히 내용/이름에 포함 여부로 체크)
        stream = applyContainsFilter(stream, region);
        stream = applyContainsFilter(stream, category);
        stream = applyContainsFilter(stream, congestion);

        List<Festivals> list = stream.collect(Collectors.toList());

        // 4) 진행 중 → 예정 → 지난 순으로 정렬
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

        // 5) 지역 선택 옵션 (임시)
        List<String> regions = List.of("서울", "부산", "울산", "경남", "기타");

        // 6) 지도용 마커 데이터 (status 포함)
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

        // 7) 모델에 담기
        model.addAttribute("today", today);
        model.addAttribute("festivals", list);
        model.addAttribute("year", year);
        model.addAttribute("markers", markers);

        // 폼 값 유지
        model.addAttribute("region", region);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("category", category);
        model.addAttribute("congestion", congestion);
        model.addAttribute("regions", regions);

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
 // 캘린더
    @GetMapping("/calendar")
    public String calendar(
            @RequestParam(name = "year",  required = false) Integer yearParam,
            @RequestParam(name = "month", required = false) Integer monthParam,
            @RequestParam(name = "day",   required = false) Integer dayParam,  // ✅ 추가
            Model model) {

        LocalDate today = LocalDate.now();

        int year  = (yearParam  != null) ? yearParam  : today.getYear();
        int month = (monthParam != null) ? monthParam : today.getMonthValue();

        // 달 범위
        LocalDate calendarStart = LocalDate.of(year, month, 1);
        LocalDate calendarEnd   = calendarStart.withDayOfMonth(calendarStart.lengthOfMonth());

        // 선택된 날짜 (없으면: 이번 달이면 오늘, 아니면 1일)
        int selectedDay;
        if (dayParam != null) {
            selectedDay = dayParam;
        } else if (year == today.getYear() && month == today.getMonthValue()) {
            selectedDay = today.getDayOfMonth();
        } else {
            selectedDay = 1;
        }
        LocalDate selectedDate = LocalDate.of(year, month, selectedDay);

        // 이 달과 '기간이 겹치는' 축제들 조회
        //    (repo 에 아래 메소드 하나 만들어주는 게 베스트)
        //    List<Festivals> findByFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(
        //            LocalDate end, LocalDate start);
        List<Festivals> list =
                repository.findByFstvlBeginDeLessThanEqualAndFstvlEndDeGreaterThanEqual(
                        calendarEnd, calendarStart);

        // 날짜별로 축제 묶기 (여러 날 열리는 축제는 날짜마다 넣기)
        Map<LocalDate, List<Festivals>> dailyMap = new HashMap<>();

        for (Festivals f : list) {
            LocalDate s = f.getFstvlBeginDe();
            LocalDate e = f.getFstvlEndDe();
            if (s == null || e == null) continue;

            LocalDate from = s.isBefore(calendarStart) ? calendarStart : s;
            LocalDate to   = e.isAfter(calendarEnd)   ? calendarEnd   : e;

            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                dailyMap.computeIfAbsent(d, k -> new ArrayList<>()).add(f);
            }
        }

        // 화면 아래에 보여줄 "선택 날짜의 축제들"
        List<Festivals> dailyFestivals =
                dailyMap.getOrDefault(selectedDate, Collections.emptyList());

        model.addAttribute("calendarStart", calendarStart);
        model.addAttribute("calendarEnd",   calendarEnd);
        model.addAttribute("festivalMap",   dailyMap);       // 날짜 칸에 표시할 용도
        model.addAttribute("year",          year);
        model.addAttribute("month",         month);
        model.addAttribute("selectedDate",  selectedDate);   // 선택된 날짜
        model.addAttribute("dailyFestivals", dailyFestivals);// 그 날 축제 목록

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

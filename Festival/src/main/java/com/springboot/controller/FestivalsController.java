package com.springboot.controller;

import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalMaster;
import com.springboot.domain.FestivalStatus;
import com.springboot.domain.Member;
import com.springboot.dto.FestivalMarker;
import com.springboot.dto.ReviewResponse;
import com.springboot.repository.FestivalEventRepository;
import com.springboot.repository.FestivalReviewRepository;
import com.springboot.service.FestivalPatternService;
import com.springboot.service.FestivalPatternService.FestivalPatternResult;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/festivals")
@RequiredArgsConstructor
public class FestivalsController {

    private final FestivalEventRepository eventRepository;
    private final FestivalReviewRepository reviewRepository;
    private final FestivalPatternService patternService;

    // 메인 페이지
    @GetMapping
    public String festivalMain(Model model) {
        LocalDate today = LocalDate.now();
        YearMonth nowMonth = YearMonth.now();

        List<FestivalEvent> ongoingFestivals =
                eventRepository.findOngoing(today, PageRequest.of(0, 8));

        LocalDate start = nowMonth.atDay(1);
        LocalDate end = nowMonth.atEndOfMonth();

        List<FestivalEvent> monthlyFestivals =
                eventRepository.findByStartBetween(start, end, PageRequest.of(0, 8));

        model.addAttribute("ongoingFestivals", ongoingFestivals);
        model.addAttribute("monthlyFestivals", monthlyFestivals);
        model.addAttribute("year", nowMonth.getYear());

        return "festivals-home";
    }

    // 리스트 기반 검색 화면 (진행 중인 축제만 기본 표시)
    @GetMapping("/list")
    public String list(
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "congestion", required = false) String congestion,
            @RequestParam(name = "viewYear", required = false) Integer viewYear,
            @RequestParam(name = "showAll", required = false, defaultValue = "false") String showAll,
            Model model
    ) {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        // showAll이 true가 아니면 진행 중인 축제만 표시
        boolean displayAll = "true".equalsIgnoreCase(showAll);

        int year;
        if (viewYear != null) year = viewYear;
        else if (startDate != null) year = startDate.getYear();
        else if (endDate != null) year = endDate.getYear();
        else year = currentYear;

        LocalDate rangeStart = (startDate != null) ? startDate : LocalDate.of(year, 1, 1);
        LocalDate rangeEnd   = (endDate != null)   ? endDate   : LocalDate.of(year, 12, 31);

        List<FestivalEvent> base;
        
        if (displayAll) {
            // 전체 축제 조회
            base = eventRepository.findOverlapping(rangeStart, rangeEnd);
        } else {
            // 진행 중인 축제만 조회
            base = eventRepository.findOverlapping(rangeStart, rangeEnd)
                    .stream()
                    .filter(e -> {
                        FestivalStatus status = calculateStatus(e, today);
                        return status == FestivalStatus.ONGOING;
                    })
                    .collect(Collectors.toList());
        }

        Stream<FestivalEvent> stream = base.stream();

        if (region != null && !region.isBlank()) {
            String r = region.trim();
            stream = stream.filter(e -> {
                FestivalMaster m = e.getMaster();
                if (m == null) return false;
                String ctprvn = safe(m.getCtprvnNm());
                String signgu = safe(m.getSignguNm());
                return ctprvn.contains(r) || signgu.contains(r) || safe(m.getAddr1()).contains(r);
            });
        }

        stream = applyContainsFilter(stream, category);
        stream = applyContainsFilter(stream, congestion);

        List<FestivalEvent> list = stream.collect(Collectors.toList());

        list.sort(Comparator
                .comparing((FestivalEvent e) -> {
                    FestivalStatus status = calculateStatus(e, today);
                    return switch (status) {
                        case ONGOING -> 0;
                        case UPCOMING -> 1;
                        case PAST -> 2;
                    };
                })
                .thenComparing(FestivalEvent::getFstvlStart,
                        Comparator.nullsLast(Comparator.naturalOrder()))
        );

        List<FestivalMarker> markers = list.stream()
                .map(e -> {
                    FestivalMaster m = e.getMaster();
                    if (m == null) return null;

                    Double lat = m.getMapY();
                    Double lng = m.getMapX();
                    if (lat == null || lng == null) return null;

                    FestivalStatus status = calculateStatus(e, today);

                    return new FestivalMarker(
                            e.getId(),
                            safe(m.getFstvlNm()),
                            lat,
                            lng,
                            status.name()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<String> regions = List.of("서울", "부산", "울산", "경남", "기타");

        model.addAttribute("today", today);
        model.addAttribute("festivals", list);
        model.addAttribute("markers", markers);
        model.addAttribute("showAll", showAll);

        model.addAttribute("year", year);
        model.addAttribute("region", region);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("category", category);
        model.addAttribute("congestion", congestion);
        model.addAttribute("regions", regions);
        model.addAttribute("isFutureYear", year > currentYear);

        return "list";
    }

    // 캘린더 (패턴 분석 기능 통합)
    @GetMapping("/calendar")
    public String calendar(
            @RequestParam(name = "year", required = false) Integer yearParam,
            @RequestParam(name = "month", required = false) Integer monthParam,
            @RequestParam(name = "day", required = false) Integer dayParam,
            @RequestParam(name = "mode", required = false, defaultValue = "all") String mode,
            Model model
    ) {
        LocalDate today = LocalDate.now();
        int baseYear = (yearParam != null) ? yearParam : today.getYear();
        int baseMonth = (monthParam != null) ? monthParam : today.getMonthValue();

        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.of(baseYear, baseMonth);
        } catch (Exception e) {
            yearMonth = YearMonth.from(today);
        }

        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        YearMonth prevMonth = yearMonth.minusMonths(1);
        YearMonth nextMonth = yearMonth.plusMonths(1);

        // 실제 + 예상 축제 모두 조회
        List<FestivalEvent> allEvents = eventRepository.findOverlapping(monthStart, monthEnd);

        // 모드에 따라 필터링
        if ("real".equalsIgnoreCase(mode)) {
            allEvents = allEvents.stream()
                    .filter(e -> e.getFcltyNm() == null || !e.getFcltyNm().startsWith("[예상]"))
                    .collect(Collectors.toList());
        } else if ("expected".equalsIgnoreCase(mode)) {
            allEvents = allEvents.stream()
                    .filter(e -> e.getFcltyNm() != null && e.getFcltyNm().startsWith("[예상]"))
                    .collect(Collectors.toList());
        }

        // festivalMap 생성 (날짜별로 축제 그룹화)
        Map<LocalDate, List<FestivalEvent>> festivalMap = new HashMap<>();

        for (FestivalEvent e : allEvents) {
            LocalDate begin = e.getFstvlStart();
            LocalDate end = e.getFstvlEnd();
            if (begin == null || end == null) continue;

            LocalDate effectiveStart = begin.isBefore(monthStart) ? monthStart : begin;
            LocalDate effectiveEnd = end.isAfter(monthEnd) ? monthEnd : end;

            for (LocalDate d = effectiveStart; !d.isAfter(effectiveEnd); d = d.plusDays(1)) {
                festivalMap.computeIfAbsent(d, k -> new ArrayList<>()).add(e);
            }
        }

        // 각 날짜별 축제 정렬
        for (List<FestivalEvent> events : festivalMap.values()) {
            events.sort(Comparator
                    .comparing(FestivalEvent::getFstvlStart, Comparator.nullsLast(LocalDate::compareTo))
                    .thenComparing(e -> safe(e.getMaster() != null ? e.getMaster().getFstvlNm() : ""), String::compareTo)
            );
        }

        // 선택된 날짜 결정
        LocalDate selectedDate;
        if (dayParam != null) {
            try {
                selectedDate = LocalDate.of(yearMonth.getYear(), yearMonth.getMonthValue(), dayParam);
            } catch (Exception e) {
                selectedDate = monthStart;
            }
        } else {
            if (today.getYear() == yearMonth.getYear() && today.getMonthValue() == yearMonth.getMonthValue()) {
                selectedDate = today;
            } else {
                selectedDate = monthStart;
            }
        }

        // ☆☆☆ 이제 여기서 dailyFestivals를 가져옴 (festivalMap이 생성된 후!)
        List<FestivalEvent> dailyFestivals = festivalMap.getOrDefault(selectedDate, Collections.emptyList());

        // 디버깅 로그
        System.out.println("===== 캘린더 디버깅 =====");
        System.out.println("조회 기간: " + monthStart + " ~ " + monthEnd);
        System.out.println("선택된 날짜: " + selectedDate);
        System.out.println("해당 날짜의 축제 수: " + dailyFestivals.size());
        dailyFestivals.forEach(f -> 
            System.out.println("  - " + f.getFcltyNm() + " (" + f.getFstvlStart() + " ~ " + f.getFstvlEnd() + ")")
        );
        System.out.println("========================");

        // 패턴 분석 추가
        List<DailyPatternInfo> dailyPatterns = new ArrayList<>();
        
        for (FestivalEvent event : dailyFestivals) {
            FestivalMaster master = event.getMaster();
            if (master == null) continue;

            FestivalPatternResult pattern = patternService.analyzeFestivalPattern(master, 2019, 2025);

            if (pattern.isValid()) {
                dailyPatterns.add(new DailyPatternInfo(
                    event.getId(),
                    master.getFstvlNm(),
                    event.getFstvlStart(),
                    event.getFstvlEnd(),
                    pattern
                ));
            }
        }

        dailyPatterns.sort(Comparator
            .comparing(DailyPatternInfo::getPatternConfidence).reversed()
            .thenComparing(DailyPatternInfo::getFestivalName)
        );

        // Model에 데이터 추가
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
        model.addAttribute("dailyPatterns", dailyPatterns);
        model.addAttribute("mode", mode);

        return "calendar";
    }

    // 상세 페이지 (리뷰 목록 추가)
    @GetMapping("/{eventId}")
    public String detail(@PathVariable("eventId") Long eventId, HttpSession session, Model model) {
        FestivalEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("FestivalEvent not found: " + eventId));

        List<ReviewResponse> reviews = reviewRepository
                .findByEventOrderByCreatedAtDesc(event)
                .stream()
                .map(ReviewResponse::from)
                .collect(Collectors.toList());

        model.addAttribute("festival", event);
        model.addAttribute("reviews", reviews);
        
        Member member = (Member) session.getAttribute("member");
        if (member != null) {
            model.addAttribute("member", member);
        }
        
        return "detail";
    }

    // ===== helpers =====
    private Stream<FestivalEvent> applyContainsFilter(Stream<FestivalEvent> stream, String keyword) {
        if (keyword == null || keyword.isBlank()) return stream;
        String kw = keyword.trim();

        return stream.filter(e -> {
            FestivalMaster m = e.getMaster();
            if (m == null) return false;

            return safe(m.getFstvlNm()).contains(kw)
                    || safe(m.getAddr1()).contains(kw)
                    || safe(m.getOverview()).contains(kw);
        });
    }

    private FestivalStatus calculateStatus(FestivalEvent e, LocalDate today) {
        LocalDate begin = e.getFstvlStart();
        LocalDate end = e.getFstvlEnd();

        if (begin == null || end == null) return FestivalStatus.PAST;

        if (!today.isBefore(begin) && !today.isAfter(end)) return FestivalStatus.ONGOING;
        if (today.isBefore(begin)) return FestivalStatus.UPCOMING;
        return FestivalStatus.PAST;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    /**
     * 날짜별 패턴 분석 정보를 담는 내부 클래스
     */
    public static class DailyPatternInfo {
        private final Long eventId;
        private final String festivalName;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final FestivalPatternResult pattern;

        public DailyPatternInfo(Long eventId, String festivalName, 
                               LocalDate startDate, LocalDate endDate,
                               FestivalPatternResult pattern) {
            this.eventId = eventId;
            this.festivalName = festivalName;
            this.startDate = startDate;
            this.endDate = endDate;
            this.pattern = pattern;
        }

        public Long getEventId() { return eventId; }
        public String getFestivalName() { return festivalName; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public FestivalPatternResult getPattern() { return pattern; }
        
        // 편의 메서드들
        public int getPatternConfidence() { 
            return pattern.getPatternConfidence(); 
        }
        
        public String getExpectedPeriod() { 
            return pattern.getExpectedPeriod(); 
        }
        
        public int getOccurrenceCount() { 
            return pattern.getOccurrenceCount(); 
        }
        
        public String getYears() { 
            return pattern.getYears(); 
        }
        
        public String getMonthConsistency() { 
            return pattern.getMonthConsistency(); 
        }
        
        public String getWeekConsistency() { 
            return pattern.getWeekConsistency(); 
        }
        
        public String getDayConsistency() { 
            return pattern.getDayConsistency(); 
        }
        
        public int getAverageDuration() { 
            return pattern.getAverageDuration(); 
        }
    }
}
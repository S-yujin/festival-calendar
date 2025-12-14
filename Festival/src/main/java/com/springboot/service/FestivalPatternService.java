package com.springboot.service;

import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalMaster;
import com.springboot.repository.FestivalEventRepository;
import com.springboot.repository.FestivalMasterRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FestivalPatternService {

    private final FestivalEventRepository repository;
    private final FestivalEventRepository eventRepository;
    private final FestivalMasterRepository masterRepository;

    public FestivalPatternService(FestivalEventRepository repository,
                                  FestivalMasterRepository masterRepository) {
        this.repository = repository;
        this.eventRepository = repository;
        this.masterRepository = masterRepository;
    }

    public static class ExpectedPeriod {
        private final String baseName;
        private final int sampleCount;
        private final int targetYear;
        private final int month;
        private final int weekOfMonth;
        private final DayOfWeek dayOfWeek;
        private final String dayOfWeekKo;

        public ExpectedPeriod(String baseName, int sampleCount,
                              int targetYear, int month,
                              int weekOfMonth, DayOfWeek dayOfWeek,
                              String dayOfWeekKo) {
            this.baseName = baseName;
            this.sampleCount = sampleCount;
            this.targetYear = targetYear;
            this.month = month;
            this.weekOfMonth = weekOfMonth;
            this.dayOfWeek = dayOfWeek;
            this.dayOfWeekKo = dayOfWeekKo;
        }

        public String getBaseName() { return baseName; }
        public int getSampleCount() { return sampleCount; }
        public int getTargetYear() { return targetYear; }
        public int getMonth() { return month; }
        public int getWeekOfMonth() { return weekOfMonth; }
        public DayOfWeek getDayOfWeek() { return dayOfWeek; }
        public String getDayOfWeekKo() { return dayOfWeekKo; }
    }

    private static class Key {
        final int month;
        final int weekOfMonth;
        final DayOfWeek dayOfWeek;

        Key(int month, int weekOfMonth, DayOfWeek dayOfWeek) {
            this.month = month;
            this.weekOfMonth = weekOfMonth;
            this.dayOfWeek = dayOfWeek;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return month == key.month &&
                    weekOfMonth == key.weekOfMonth &&
                    dayOfWeek == key.dayOfWeek;
        }

        @Override
        public int hashCode() {
            return Objects.hash(month, weekOfMonth, dayOfWeek);
        }
    }

    public Optional<ExpectedPeriod> predictNextYearByName(String festivalName, int targetYear) {
        String baseName = normalizeName(festivalName);

        List<FestivalEvent> history = repository.findByFcltyNmContaining(baseName);

        if (history.size() < 3) return Optional.empty();

        Map<Key, Long> counts = new HashMap<>();

        for (FestivalEvent f : history) {
            LocalDate d = f.getFstvlStart();
            if (d == null) continue;

            int month = d.getMonthValue();
            int weekOfMonth = (d.getDayOfMonth() - 1) / 7 + 1;
            DayOfWeek dow = d.getDayOfWeek();

            Key key = new Key(month, weekOfMonth, dow);
            counts.put(key, counts.getOrDefault(key, 0L) + 1L);
        }

        if (counts.isEmpty()) return Optional.empty();

        Map.Entry<Key, Long> best = null;
        for (Map.Entry<Key, Long> e : counts.entrySet()) {
            if (best == null || e.getValue() > best.getValue()) best = e;
        }

        Key k = best.getKey();

        return Optional.of(new ExpectedPeriod(
                baseName,
                history.size(),
                targetYear,
                k.month,
                k.weekOfMonth,
                k.dayOfWeek,
                toKorean(k.dayOfWeek)
        ));
    }

    public Optional<ExpectedPeriod> predictNextYearByName(String festivalName) {
        int latestYear = findLatestYearInDb();
        return predictNextYearByName(festivalName, latestYear + 1);
    }

    /**
     * 성능 최적화: 요청된 월의 과거 축제만 조회하여 예측
     */
    public List<FestivalEvent> buildExpectedFestivalsForRange(LocalDate futureStart, LocalDate futureEnd) {
        if (futureStart == null || futureEnd == null || futureEnd.isBefore(futureStart)) {
            return Collections.emptyList();
        }

        List<FestivalEvent> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int latestYearInDb = findLatestYearInDb();

        // 요청 기간에 포함되는 모든 연도 추출
        Set<Integer> targetYears = new LinkedHashSet<>();
        for (LocalDate d = futureStart; !d.isAfter(futureEnd); d = d.plusMonths(1)) {
            targetYears.add(d.getYear());
        }

        // 요청 기간의 월 추출 (중복 제거)
        Set<Integer> targetMonths = new LinkedHashSet<>();
        for (LocalDate d = futureStart; !d.isAfter(futureEnd); d = d.plusMonths(1)) {
            targetMonths.add(d.getMonthValue());
        }

        // 패턴이 있는 모든 Master 조회
        List<FestivalMaster> mastersWithPattern = masterRepository.findAll().stream()
            .filter(m -> m.getExpectedMonth() != null && targetMonths.contains(m.getExpectedMonth()))
            .collect(java.util.stream.Collectors.toList());

        // 각 연도에 대해 축제 예측
        for (Integer targetYear : targetYears) {
            for (FestivalMaster master : mastersWithPattern) {
                try {
                    // 예측된 시작일 계산
                    LocalDate predictedStart = nthWeekdayOfMonth(
                            targetYear,
                            master.getExpectedMonth(),
                            master.getExpectedWeekOfMonth(),
                            master.getExpectedDayOfWeek()
                    );

                    // 요청 기간에 포함되는지 확인
                    if (predictedStart.isBefore(futureStart) || predictedStart.isAfter(futureEnd)) {
                        continue;
                    }

                    // 지속 기간
                    int duration = master.getExpectedDurationDays() != null ? 
                        master.getExpectedDurationDays() : 0;
                        
                    // =========================================================================
                    // 🚨 수정된 부분 (문제 해결) 🚨
                    // Master에 저장된 duration이 '1년 주기' (365일)로 잘못 저장되어 
                    // 다음 해로 넘어가는 것을 방지합니다.
                    if (duration > 360) {
                        duration = 0; // 1일 축제(오프셋 0)로 강제하여 2027년으로 넘어가는 것을 방지
                    }
                    // =========================================================================

                    LocalDate predictedEnd = predictedStart.plusDays(duration);

                    // 중복 방지
                    String key = master.getFstvlNm() + "|" + predictedStart;
                    if (!seen.add(key)) continue;

                    FestivalEvent expected = FestivalEvent.createExpected(
                            master.getFstvlNm(), 
                            predictedStart, 
                            predictedEnd
                    );
                    result.add(expected);
                } catch (Exception e) {
                    // 날짜 계산 오류 무시
                }
            }
        }

        result.sort(Comparator.comparing(FestivalEvent::getFstvlStart,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return result;
    }

    private static class BaseYearPick {
        final int baseYear;
        final List<FestivalEvent> baseFestivals;

        BaseYearPick(int baseYear, List<FestivalEvent> baseFestivals) {
            this.baseYear = baseYear;
            this.baseFestivals = baseFestivals;
        }
    }

    private BaseYearPick pickBaseYearForMonth(int month, int latestYear) {
        int earliest = Math.max(1900, latestYear - 30);

        for (int y = latestYear; y >= earliest; y--) {
            LocalDate s = LocalDate.of(y, month, 1);
            LocalDate e = s.withDayOfMonth(s.lengthOfMonth());

            List<FestivalEvent> list = eventRepository.findByStartBetween(s, e, PageRequest.of(0, 5000));

            if (list != null && !list.isEmpty()) {
                return new BaseYearPick(y, list);
            }
        }
        return new BaseYearPick(latestYear, Collections.emptyList());
    }

    private int findLatestYearInDb() {
        try {
            Optional<FestivalEvent> latest = eventRepository.findTopByOrderByFstvlStartDesc();
            if (latest.isPresent() && latest.get().getFstvlStart() != null) {
                return latest.get().getFstvlStart().getYear();
            }
        } catch (Exception ignored) { }
        return LocalDate.now().getYear();
    }

    private LocalDate nthWeekdayOfMonth(int year, int month, int weekOfMonth, DayOfWeek dayOfWeek) {
        LocalDate first = LocalDate.of(year, month, 1);
        int diff = (dayOfWeek.getValue() - first.getDayOfWeek().getValue() + 7) % 7;
        LocalDate firstDow = first.plusDays(diff);
        LocalDate date = firstDow.plusWeeks(weekOfMonth - 1);

        if (date.getMonthValue() != month) {
            date = date.minusWeeks(1);
        }
        return date;
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        String n = name;
        n = n.replaceAll("제\\d+회", "");
        n = n.replaceAll("\\d{4}", "");
        n = n.replaceAll("\\[예상\\]\\s*", "");
        return n.trim();
    }

    private String toKorean(DayOfWeek dow) {
        if (dow == null) return "";
        return switch (dow) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }

    /**
     * 특정 축제의 개최 패턴 분석 (기존 메서드)
     */
    public PatternAnalysisResult analyzeFestivalPattern(FestivalMaster master) {
        if (master == null) {
            return PatternAnalysisResult.invalid();
        }
        
        List<FestivalEvent> events = eventRepository.findByMaster(master);
        
        if (events.size() < 3) {
            return PatternAnalysisResult.invalid();
        }
        
        // 월-주차-요일 패턴 카운트
        Map<Key, Integer> patternCounts = new HashMap<>();
        Map<Key, List<Integer>> durationsByPattern = new HashMap<>();
        
        for (FestivalEvent event : events) {
            LocalDate start = event.getFstvlStart();
            LocalDate end = event.getFstvlEnd();
            
            if (start == null || end == null) continue;
            
            int month = start.getMonthValue();
            int weekOfMonth = (start.getDayOfMonth() - 1) / 7 + 1;
            DayOfWeek dayOfWeek = start.getDayOfWeek();
            
            Key key = new Key(month, weekOfMonth, dayOfWeek);
            patternCounts.merge(key, 1, Integer::sum);
            
            // 지속 기간 수집
            int duration = (int) ChronoUnit.DAYS.between(start, end);
            durationsByPattern.computeIfAbsent(key, k -> new ArrayList<>()).add(duration);
        }
        
        if (patternCounts.isEmpty()) {
            return PatternAnalysisResult.invalid();
        }
        
        // 가장 빈번한 패턴 찾기
        Key mostFrequent = Collections.max(
            patternCounts.entrySet(), 
            Map.Entry.comparingByValue()
        ).getKey();
        
        int sampleCount = patternCounts.get(mostFrequent);
        
        // 평균 지속 기간
        List<Integer> durations = durationsByPattern.get(mostFrequent);
        int avgDuration = durations != null && !durations.isEmpty() ?
            (int) durations.stream().mapToInt(Integer::intValue).average().orElse(0) : 0;
        
        return new PatternAnalysisResult(
            true,
            sampleCount,
            mostFrequent.month,
            mostFrequent.weekOfMonth,
            mostFrequent.dayOfWeek,
            avgDuration
        );
    }

    // ============ 패턴 분석 기능 추가 ============

    /**
     * 전체 축제의 개최 패턴을 분석하여 예상 개최 시기를 생성
     */
    public List<FestivalPatternResult> analyzeAllFestivalPatterns(int startYear, int endYear) {
        List<FestivalPatternResult> results = new ArrayList<>();
        
        // 모든 Master 조회
        List<FestivalMaster> allMasters = masterRepository.findAll();
        
        for (FestivalMaster master : allMasters) {
            FestivalPatternResult result = analyzeFestivalPattern(master, startYear, endYear);
            if (result.isValid() && result.getOccurrenceCount() >= 2) {
                results.add(result);
            }
        }
        
        // 개최횟수 내림차순, 패턴신뢰도 내림차순, 최근개최일 내림차순
        results.sort(Comparator
            .comparing(FestivalPatternResult::getOccurrenceCount).reversed()
            .thenComparing(FestivalPatternResult::getPatternConfidence).reversed()
            .thenComparing(FestivalPatternResult::getLatestDate, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        
        return results;
    }

    /**
     * 특정 축제의 개최 패턴 분석 및 예상 개최 시기 생성 (연도 범위 지정)
     */
    public FestivalPatternResult analyzeFestivalPattern(FestivalMaster master, int startYear, int endYear) {
        if (master == null) {
            return FestivalPatternResult.invalid();
        }

        // 해당 기간의 이벤트 조회
        List<FestivalEvent> events = eventRepository.findByMaster(master).stream()
            .filter(e -> e.getFstvlStart() != null)
            .filter(e -> {
                int year = e.getFstvlStart().getYear();
                return year >= startYear && year <= endYear;
            })
            .collect(Collectors.toList());

        if (events.size() < 3) {
            return FestivalPatternResult.invalid();
        }

        return analyzePattern(master, events);
    }

    private FestivalPatternResult analyzePattern(FestivalMaster master, List<FestivalEvent> events) {
        // 1. 각 이벤트의 월, 주차, 요일 추출
        List<PatternDetail> details = events.stream()
            .map(this::extractPatternDetail)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (details.isEmpty()) {
            return FestivalPatternResult.invalid();
        }

        // 2. 월별 빈도 분석
        Map<Integer, Long> monthFreq = details.stream()
            .collect(Collectors.groupingBy(PatternDetail::getMonth, Collectors.counting()));
        
        // 3. 주차별 빈도 분석
        Map<Integer, Long> weekFreq = details.stream()
            .collect(Collectors.groupingBy(PatternDetail::getWeekOfMonth, Collectors.counting()));
        
        // 4. 요일별 빈도 분석
        Map<DayOfWeek, Long> dayFreq = details.stream()
            .collect(Collectors.groupingBy(PatternDetail::getDayOfWeek, Collectors.counting()));

        // 5. 최빈값 추출
        int mostFrequentMonth = findMostFrequent(monthFreq);
        int mostFrequentWeek = findMostFrequent(weekFreq);
        DayOfWeek mostFrequentDay = findMostFrequentDay(dayFreq);

        // 6. 일관성 분석
        int uniqueMonths = monthFreq.size();
        int uniqueWeeks = weekFreq.size();
        int uniqueDays = dayFreq.size();

        String monthConsistency = getConsistency(uniqueMonths);
        String weekConsistency = getConsistency(uniqueWeeks);
        String dayConsistency = getConsistency(uniqueDays);

        // 7. 평균 지속 기간 계산
        int avgDuration = calculateAverageDuration(events);

        // 8. 예상 개최 시기 문자열 생성
        String expectedPeriod = generateExpectedPeriod(
            uniqueMonths, uniqueWeeks, uniqueDays,
            mostFrequentMonth, mostFrequentWeek, mostFrequentDay
        );

        // 9. 패턴 신뢰도 계산 (0-100점)
        int confidence = calculateConfidence(uniqueMonths, uniqueWeeks, uniqueDays);

        // 10. 개최 연도 목록
        String years = events.stream()
            .map(e -> String.valueOf(e.getFstvlStart().getYear()))
            .distinct()
            .sorted()
            .collect(Collectors.joining(","));

        // 11. 첫 개최일, 최근 개최일
        LocalDate firstDate = events.stream()
            .map(FestivalEvent::getFstvlStart)
            .min(LocalDate::compareTo)
            .orElse(null);

        LocalDate latestDate = events.stream()
            .map(FestivalEvent::getFstvlStart)
            .max(LocalDate::compareTo)
            .orElse(null);

        return FestivalPatternResult.builder()
            .valid(true)
            .masterId(master.getId())
            .festivalName(master.getFstvlNm())
            .ctprvnNm(master.getCtprvnNm())
            .signguNm(master.getSignguNm())
            .occurrenceCount(events.size())
            .firstDate(firstDate)
            .latestDate(latestDate)
            .years(years)
            .monthPattern(details.stream().map(PatternDetail::getMonth).map(String::valueOf).collect(Collectors.joining(",")))
            .weekPattern(details.stream().map(PatternDetail::getWeekOfMonth).map(String::valueOf).collect(Collectors.joining(",")))
            .dayPattern(details.stream().map(d -> toKorean(d.getDayOfWeek())).collect(Collectors.joining(",")))
            .mostFrequentMonth(mostFrequentMonth)
            .monthFrequency(monthFreq.get(mostFrequentMonth).intValue())
            .mostFrequentWeek(mostFrequentWeek)
            .weekFrequency(weekFreq.get(mostFrequentWeek).intValue())
            .mostFrequentDay(mostFrequentDay)
            .dayFrequency(dayFreq.get(mostFrequentDay).intValue())
            .monthConsistency(monthConsistency)
            .weekConsistency(weekConsistency)
            .dayConsistency(dayConsistency)
            .expectedPeriod(expectedPeriod)
            .patternConfidence(confidence)
            .averageDuration(avgDuration)
            .build();
    }

    /**
     * 이벤트로부터 패턴 상세 정보 추출
     */
    private PatternDetail extractPatternDetail(FestivalEvent event) {
        LocalDate start = event.getFstvlStart();
        if (start == null) return null;

        int year = start.getYear();
        int month = start.getMonthValue();
        int weekOfMonth = (start.getDayOfMonth() - 1) / 7 + 1;
        DayOfWeek dayOfWeek = start.getDayOfWeek();

        return new PatternDetail(year, month, weekOfMonth, dayOfWeek);
    }

    /**
     * 평균 지속 기간 계산
     */
    private int calculateAverageDuration(List<FestivalEvent> events) {
        List<Long> durations = events.stream()
            .filter(e -> e.getFstvlStart() != null && e.getFstvlEnd() != null)
            .map(e -> ChronoUnit.DAYS.between(e.getFstvlStart(), e.getFstvlEnd()))
            .collect(Collectors.toList());

        if (durations.isEmpty()) return 0;

        return (int) durations.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
    }

    /**
     * 예상 개최 시기 문자열 자동 생성
     */
    private String generateExpectedPeriod(int uniqueMonths, int uniqueWeeks, int uniqueDays,
                                         int month, int week, DayOfWeek day) {
        // 월, 주차, 요일 모두 고정
        if (uniqueMonths == 1 && uniqueWeeks == 1 && uniqueDays == 1) {
            return String.format("%d월 %d주차 %s", month, week, toKorean(day));
        }
        
        // 월과 주차만 고정
        if (uniqueMonths == 1 && uniqueWeeks == 1) {
            return String.format("%d월 %d주차 %s 전후", month, week, toKorean(day));
        }
        
        // 월만 고정
        if (uniqueMonths == 1) {
            return String.format("%d월 %d주차 전후", month, week);
        }
        
        // 월이 거의 고정 (1-2개월)
        if (uniqueMonths <= 2) {
            return String.format("%d월 경 (%d주차 전후)", month, week);
        }
        
        // 패턴 불규칙
        return String.format("매년 %d월 전후 (패턴 불규칙)", month);
    }

    /**
     * 패턴 신뢰도 점수 계산 (0-100)
     */
    private int calculateConfidence(int uniqueMonths, int uniqueWeeks, int uniqueDays) {
        int monthScore = switch (uniqueMonths) {
            case 1 -> 40;
            case 2 -> 30;
            default -> 10;
        };

        int weekScore = switch (uniqueWeeks) {
            case 1 -> 30;
            case 2 -> 20;
            default -> 5;
        };

        int dayScore = switch (uniqueDays) {
            case 1 -> 30;
            case 2 -> 20;
            default -> 5;
        };

        return monthScore + weekScore + dayScore;
    }

    /**
     * 일관성 문자열 반환
     */
    private String getConsistency(int uniqueCount) {
        return switch (uniqueCount) {
            case 1 -> "고정";
            case 2 -> "거의고정";
            default -> "변동";
        };
    }

    /**
     * Map에서 최빈값(가장 빈번한 값) 찾기
     */
    private <K> K findMostFrequent(Map<K, Long> freqMap) {
        return freqMap.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private DayOfWeek findMostFrequentDay(Map<DayOfWeek, Long> freqMap) {
        return freqMap.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * 패턴 상세 정보를 담는 내부 클래스
     */
    private static class PatternDetail {
        private final int year;
        private final int month;
        private final int weekOfMonth;
        private final DayOfWeek dayOfWeek;

        public PatternDetail(int year, int month, int weekOfMonth, DayOfWeek dayOfWeek) {
            this.year = year;
            this.month = month;
            this.weekOfMonth = weekOfMonth;
            this.dayOfWeek = dayOfWeek;
        }

        public int getYear() { return year; }
        public int getMonth() { return month; }
        public int getWeekOfMonth() { return weekOfMonth; }
        public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    }

    // ============================================

    /**
     * 패턴 분석 결과 클래스 (기존)
     */
    public static class PatternAnalysisResult {
        private final boolean valid;
        private final int sampleCount;
        private final int month;
        private final int weekOfMonth;
        private final DayOfWeek dayOfWeek;
        private final int durationDays;
        
        private PatternAnalysisResult(boolean valid, int sampleCount, int month, 
                                     int weekOfMonth, DayOfWeek dayOfWeek, int durationDays) {
            this.valid = valid;
            this.sampleCount = sampleCount;
            this.month = month;
            this.weekOfMonth = weekOfMonth;
            this.dayOfWeek = dayOfWeek;
            this.durationDays = durationDays;
        }
        
        public static PatternAnalysisResult invalid() {
            return new PatternAnalysisResult(false, 0, 0, 0, null, 0);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public int getSampleCount() { return sampleCount; }
        public int getMonth() { return month; }
        public int getWeekOfMonth() { return weekOfMonth; }
        public DayOfWeek getDayOfWeek() { return dayOfWeek; }
        public int getDurationDays() { return durationDays; }
    }

    /**
     * 축제 패턴 분석 결과 클래스 (신규 - 상세 정보 포함)
     */
    public static class FestivalPatternResult {
        private final boolean valid;
        private final Long masterId;
        private final String festivalName;
        private final String ctprvnNm;
        private final String signguNm;
        private final int occurrenceCount;
        private final LocalDate firstDate;
        private final LocalDate latestDate;
        private final String years;
        
        // 패턴 상세
        private final String monthPattern;
        private final String weekPattern;
        private final String dayPattern;
        
        // 최빈값
        private final int mostFrequentMonth;
        private final int monthFrequency;
        private final int mostFrequentWeek;
        private final int weekFrequency;
        private final DayOfWeek mostFrequentDay;
        private final int dayFrequency;
        
        // 일관성
        private final String monthConsistency;
        private final String weekConsistency;
        private final String dayConsistency;
        
        // 예상 개최 시기
        private final String expectedPeriod;
        private final int patternConfidence;
        private final int averageDuration;

        private FestivalPatternResult(boolean valid, Long masterId, String festivalName,
                                     String ctprvnNm, String signguNm, int occurrenceCount,
                                     LocalDate firstDate, LocalDate latestDate, String years,
                                     String monthPattern, String weekPattern, String dayPattern,
                                     int mostFrequentMonth, int monthFrequency,
                                     int mostFrequentWeek, int weekFrequency,
                                     DayOfWeek mostFrequentDay, int dayFrequency,
                                     String monthConsistency, String weekConsistency, String dayConsistency,
                                     String expectedPeriod, int patternConfidence, int averageDuration) {
            this.valid = valid;
            this.masterId = masterId;
            this.festivalName = festivalName;
            this.ctprvnNm = ctprvnNm;
            this.signguNm = signguNm;
            this.occurrenceCount = occurrenceCount;
            this.firstDate = firstDate;
            this.latestDate = latestDate;
            this.years = years;
            this.monthPattern = monthPattern;
            this.weekPattern = weekPattern;
            this.dayPattern = dayPattern;
            this.mostFrequentMonth = mostFrequentMonth;
            this.monthFrequency = monthFrequency;
            this.mostFrequentWeek = mostFrequentWeek;
            this.weekFrequency = weekFrequency;
            this.mostFrequentDay = mostFrequentDay;
            this.dayFrequency = dayFrequency;
            this.monthConsistency = monthConsistency;
            this.weekConsistency = weekConsistency;
            this.dayConsistency = dayConsistency;
            this.expectedPeriod = expectedPeriod;
            this.patternConfidence = patternConfidence;
            this.averageDuration = averageDuration;
        }

        public static FestivalPatternResult invalid() {
            return new FestivalPatternResult(false, null, null, null, null, 0, 
                null, null, null, null, null, null, 0, 0, 0, 0, null, 0,
                null, null, null, null, 0, 0);
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public boolean isValid() { return valid; }
        public Long getMasterId() { return masterId; }
        public String getFestivalName() { return festivalName; }
        public String getCtprvnNm() { return ctprvnNm; }
        public String getSignguNm() { return signguNm; }
        public int getOccurrenceCount() { return occurrenceCount; }
        public LocalDate getFirstDate() { return firstDate; }
        public LocalDate getLatestDate() { return latestDate; }
        public String getYears() { return years; }
        public String getMonthPattern() { return monthPattern; }
        public String getWeekPattern() { return weekPattern; }
        public String getDayPattern() { return dayPattern; }
        public int getMostFrequentMonth() { return mostFrequentMonth; }
        public int getMonthFrequency() { return monthFrequency; }
        public int getMostFrequentWeek() { return mostFrequentWeek; }
        public int getWeekFrequency() { return weekFrequency; }
        public DayOfWeek getMostFrequentDay() { return mostFrequentDay; }
        public int getDayFrequency() { return dayFrequency; }
        public String getMonthConsistency() { return monthConsistency; }
        public String getWeekConsistency() { return weekConsistency; }
        public String getDayConsistency() { return dayConsistency; }
        public String getExpectedPeriod() { return expectedPeriod; }
        public int getPatternConfidence() { return patternConfidence; }
        public int getAverageDuration() { return averageDuration; }

        public static class Builder {
            private boolean valid;
            private Long masterId;
            private String festivalName;
            private String ctprvnNm;
            private String signguNm;
            private int occurrenceCount;
            private LocalDate firstDate;
            private LocalDate latestDate;
            private String years;
            private String monthPattern;
            private String weekPattern;
            private String dayPattern;
            private int mostFrequentMonth;
            private int monthFrequency;
            private int mostFrequentWeek;
            private int weekFrequency;
            private DayOfWeek mostFrequentDay;
            private int dayFrequency;
            private String monthConsistency;
            private String weekConsistency;
            private String dayConsistency;
            private String expectedPeriod;
            private int patternConfidence;
            private int averageDuration;

            public Builder valid(boolean valid) { this.valid = valid; return this; }
            public Builder masterId(Long masterId) { this.masterId = masterId; return this; }
            public Builder festivalName(String festivalName) { this.festivalName = festivalName; return this; }
            public Builder ctprvnNm(String ctprvnNm) { this.ctprvnNm = ctprvnNm; return this; }
            public Builder signguNm(String signguNm) { this.signguNm = signguNm; return this; }
            public Builder occurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; return this; }
            public Builder firstDate(LocalDate firstDate) { this.firstDate = firstDate; return this; }
            public Builder latestDate(LocalDate latestDate) { this.latestDate = latestDate; return this; }
            public Builder years(String years) { this.years = years; return this; }
            public Builder monthPattern(String monthPattern) { this.monthPattern = monthPattern; return this; }
            public Builder weekPattern(String weekPattern) { this.weekPattern = weekPattern; return this; }
            public Builder dayPattern(String dayPattern) { this.dayPattern = dayPattern; return this; }
            public Builder mostFrequentMonth(int mostFrequentMonth) { this.mostFrequentMonth = mostFrequentMonth; return this; }
            public Builder monthFrequency(int monthFrequency) { this.monthFrequency = monthFrequency; return this; }
            public Builder mostFrequentWeek(int mostFrequentWeek) { this.mostFrequentWeek = mostFrequentWeek; return this; }
            public Builder weekFrequency(int weekFrequency) { this.weekFrequency = weekFrequency; return this; }
            public Builder mostFrequentDay(DayOfWeek mostFrequentDay) { this.mostFrequentDay = mostFrequentDay; return this; }
            public Builder dayFrequency(int dayFrequency) { this.dayFrequency = dayFrequency; return this; }
            public Builder monthConsistency(String monthConsistency) { this.monthConsistency = monthConsistency; return this; }
            public Builder weekConsistency(String weekConsistency) { this.weekConsistency = weekConsistency; return this; }
            public Builder dayConsistency(String dayConsistency) { this.dayConsistency = dayConsistency; return this; }
            public Builder expectedPeriod(String expectedPeriod) { this.expectedPeriod = expectedPeriod; return this; }
            public Builder patternConfidence(int patternConfidence) { this.patternConfidence = patternConfidence; return this; }
            public Builder averageDuration(int averageDuration) { this.averageDuration = averageDuration; return this; }

            public FestivalPatternResult build() {
                return new FestivalPatternResult(valid, masterId, festivalName, ctprvnNm, signguNm,
                    occurrenceCount, firstDate, latestDate, years, monthPattern, weekPattern, dayPattern,
                    mostFrequentMonth, monthFrequency, mostFrequentWeek, weekFrequency,
                    mostFrequentDay, dayFrequency, monthConsistency, weekConsistency, dayConsistency,
                    expectedPeriod, patternConfidence, averageDuration);
            }
        }
    }
}
package com.springboot.batch;

import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalMaster;
import com.springboot.repository.FestivalEventRepository;
import com.springboot.repository.FestivalMasterRepository;
import com.springboot.service.FestivalPatternService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 예상 축제 자동 생성기
 * - 애플리케이션 시작 시 1회 실행
 * - 매달 1일 자동 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpectedFestivalGenerator {

    private final FestivalPatternService patternService;
    private final FestivalEventRepository eventRepository;
    private final FestivalMasterRepository masterRepository;

    /**
     * 애플리케이션 시작 시 자동 실행
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== 예상 축제 초기 생성 시작 ===");
        generateExpectedFestivals();
    }

    /**
     * 매달 1일 새벽 2시에 자동 실행
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void scheduledGeneration() {
        log.info("=== 예상 축제 정기 생성 시작 ===");
        generateExpectedFestivals();
    }

    /**
     * 예상 축제 생성 및 DB 저장
     */
    public void generateExpectedFestivals() {
        try {
            // 1. 먼저 전체 축제 패턴 분석 (축제 이름 기준) - 별도 트랜잭션
            analyzeAllFestivalPatterns();
            
            // 2. 현재 연도 확인
            int currentYear = LocalDate.now().getYear();
            
            // DBに서 실제 축제 데이터의 최신 연도 확인
            int latestRealDataYear = eventRepository.findTopByOrderByFstvlStartDesc()
                    .map(ev -> {
                        if (ev.getFstvlStart() != null) {
                            // [예상] 태그가 없는 실제 데이터만
                            if (ev.getFcltyNm() == null || !ev.getFcltyNm().startsWith("[예상]")) {
                                return ev.getFstvlStart().getYear();
                            }
                        }
                        return currentYear;
                    })
                    .orElse(currentYear);

            log.info("현재 연도: {}, DB 실제 데이터 최신 연도: {}", currentYear, latestRealDataYear);

            // ★★★ 수정된 부분 ★★★
            // 실제 데이터의 다음 연도부터 예상 축제 생성
            // 예: 2025년까지 실제 데이터가 있으면 2026년부터 생성
            int startYear = latestRealDataYear + 1;
            int endYear = latestRealDataYear + 2;  // 2년치 생성 (예: 2026, 2027)
            
            // 현재 연도보다 과거는 생성하지 않음
            if (startYear < currentYear) {
                startYear = currentYear;
            }
            
            log.info("예상 축제 생성 범위: {}년 ~ {}년", startYear, endYear);
            
            for (int targetYear = startYear; targetYear <= endYear; targetYear++) {
                generateForYear(targetYear);
            }

            log.info("=== 예상 축제 생성 완료 ===");

        } catch (Exception e) {
            log.error("예상 축제 생성 중 오류 발생", e);
        }
    }

    /**
     * 특정 연도의 예상 축제 생성
     */
    private void generateForYear(int targetYear) {
        log.info("{}년 예상 축제 생성 중...", targetYear);

        LocalDate yearStart = LocalDate.of(targetYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(targetYear, 12, 31);
        
        // ☆☆☆ 수정된 부분: 개수만 확인하는 것이 아니라, 이미 존재하는 예상 축제 전체를 가져옴
        List<FestivalEvent> existingExpected = eventRepository.findOverlapping(yearStart, yearEnd)
                .stream()
                .filter(e -> e.getFcltyNm() != null && e.getFcltyNm().startsWith("[예상]"))
                .toList();
        
        // 이미 예상 축제가 있으면 건너뛰기
        if (!existingExpected.isEmpty()) {
            log.info("{}년 예상 축제가 이미 {}개 존재합니다. 생성을 건너뜁니다.", 
                    targetYear, existingExpected.size());
            return;
        }

        // 새로운 예상 축제 생성
        List<FestivalEvent> newExpectedEvents = patternService.buildExpectedFestivalsForRange(yearStart, yearEnd);

        if (!newExpectedEvents.isEmpty()) {
            // ☆☆☆ 추가 안전장치: 저장 전 한 번 더 중복 체크
            List<FestivalEvent> toSave = new ArrayList<>();
            
            for (FestivalEvent newEvent : newExpectedEvents) {
                boolean isDuplicate = eventRepository.existsByFcltyNmAndFstvlStartAndFstvlEnd(
                    newEvent.getFcltyNm(),
                    newEvent.getFstvlStart(),
                    newEvent.getFstvlEnd()
                );
                
                if (!isDuplicate) {
                    toSave.add(newEvent);
                } else {
                    log.debug("중복 예상 축제 건너뜀: {} ({} ~ {})", 
                        newEvent.getFcltyNm(), 
                        newEvent.getFstvlStart(), 
                        newEvent.getFstvlEnd());
                }
            }
            
            if (!toSave.isEmpty()) {
                eventRepository.saveAll(toSave);
                log.info("{}년 예상 축제 {}개 저장 완료 (중복 제외: {}개)", 
                        targetYear, toSave.size(), newExpectedEvents.size() - toSave.size());
            } else {
                log.info("{}년 예상 축제: 모두 중복으로 저장하지 않음", targetYear);
            }
        } else {
            log.info("{}년 예상 축제 없음", targetYear);
        }
    }

    /**
     * 특정 연도의 기존 예상 축제 삭제
     */
    private void deleteExistingExpectedFestivals(int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<FestivalEvent> existingExpected = eventRepository.findOverlapping(yearStart, yearEnd)
                .stream()
                .filter(e -> e.getFcltyNm() != null && e.getFcltyNm().startsWith("[예상]"))
                .toList();

        if (!existingExpected.isEmpty()) {
            eventRepository.deleteAll(existingExpected);
            log.info("{}년 기존 예상 축제 {}개 삭제", year, existingExpected.size());
        }
    }

    /**
     * 수동 실행용 메서드 (관리자 API 등에서 호출 가능)
     * force=true 시 기존 예상 축제를 삭제하고 재생성
     */
    public void regenerateForYear(int year, boolean force) {
        log.info("{}년 예상 축제 수동 재생성 (force={})", year, force);
        
        if (force) {
            // 강제 재생성: 기존 데이터 삭제 후 생성
            deleteExistingExpectedFestivals(year);
            
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = LocalDate.of(year, 12, 31);
            List<FestivalEvent> expectedEvents = patternService.buildExpectedFestivalsForRange(yearStart, yearEnd);
            
            if (!expectedEvents.isEmpty()) {
                eventRepository.saveAll(expectedEvents);
                log.info("{}년 예상 축제 {}개 저장 완료", year, expectedEvents.size());
            }
        } else {
            // 일반 생성: 중복 체크
            generateForYear(year);
        }
    }
    
    /**
     * 수동 실행용 메서드 (기본값: force=false)
     */
    public void regenerateForYear(int year) {
        regenerateForYear(year, false);
    }

    /**
     * 전체 축제 패턴 분석 및 Master 업데이트
     * 축제 이름 기준으로 모든 개최 이력을 분석
     */
    @Transactional
    private void analyzeAllFestivalPatterns() {
        log.info("=== 축제 패턴 분석 시작 (축제 이름 기준) ===");
        
        List<FestivalMaster> allMasters = masterRepository.findAll();
        int updated = 0;
        
        // 축제 이름별로 그룹화
        Map<String, List<FestivalMaster>> festivalsByName = allMasters.stream()
            .filter(m -> m.getFstvlNm() != null && !m.getFstvlNm().isBlank())
            .collect(Collectors.groupingBy(
                m -> normalizeName(m.getFstvlNm())
            ));
        
        log.info("분석 대상: {} 개의 고유 축제명", festivalsByName.size());
        
        for (Map.Entry<String, List<FestivalMaster>> entry : festivalsByName.entrySet()) {
            String normalizedName = entry.getKey();
            List<FestivalMaster> masters = entry.getValue();
            
            try {
                // 이 이름의 축제에 대한 모든 이벤트 조회
                List<FestivalEvent> allEvents = new ArrayList<>();
                for (FestivalMaster master : masters) {
                    List<FestivalEvent> events = eventRepository.findByMaster(master);
                    allEvents.addAll(events);
                }
                
                if (allEvents.size() < 3) {
                    continue; // 2회 미만 개최는 패스
                }
                
                // 패턴 분석
                PatternAnalysisResult result = analyzePattern(allEvents);
                
                if (result.isValid()) {
                    // 대표 Master 선택 (가장 최근에 개최된 Master)
                    FestivalMaster representativeMaster = selectRepresentativeMaster(masters, allEvents);
                    
                    if (representativeMaster != null) {
                        representativeMaster.setPatternSampleCount(result.sampleCount);
                        representativeMaster.setExpectedMonth(result.month);
                        representativeMaster.setExpectedWeekOfMonth(result.weekOfMonth);
                        representativeMaster.setExpectedDayOfWeek(result.dayOfWeek);
                        representativeMaster.setExpectedDurationDays(result.durationDays);
                        representativeMaster.setPatternLastUpdated(LocalDateTime.now());
                        
                        masterRepository.save(representativeMaster);
                        updated++;
                        
                        log.debug("패턴 업데이트: {} ({}회 개최) → {}월 {}주차 {}",
                            representativeMaster.getFstvlNm(),
                            result.sampleCount,
                            result.month,
                            result.weekOfMonth,
                            result.dayOfWeek);
                    }
                }
            } catch (Exception e) {
                log.warn("패턴 분석 실패: {} - {}", normalizedName, e.getMessage());
            }
        }
        
        log.info("=== 패턴 분석 완료: {}개 축제 업데이트 ===", updated);
    }
    
    /**
     * 대표 Master 선택 (가장 최근 개최된 이벤트의 Master)
     */
    private FestivalMaster selectRepresentativeMaster(List<FestivalMaster> masters, List<FestivalEvent> events) {
        // 가장 최근 이벤트 찾기
        Optional<FestivalEvent> latestEvent = events.stream()
            .filter(e -> e.getFstvlStart() != null && e.getMaster() != null)
            .max(Comparator.comparing(FestivalEvent::getFstvlStart));
        
        if (latestEvent.isPresent() && latestEvent.get().getMaster() != null) {
            // Master ID로 다시 조회하여 Proxy 문제 방지
            Long masterId = latestEvent.get().getMaster().getId();
            return masterRepository.findById(masterId).orElse(null);
        }
        
        // 최근 이벤트를 찾지 못하면 첫 번째 Master 반환
        return masters.isEmpty() ? null : masters.get(0);
    }
    
    /**
     * 이벤트 리스트를 기반으로 패턴 분석
     */
    private PatternAnalysisResult analyzePattern(List<FestivalEvent> events) {
        if (events == null || events.size() < 2) {
            return PatternAnalysisResult.invalid();
        }
        
        // 월-주차-요일 패턴 카운트
        Map<PatternKey, Integer> patternCounts = new HashMap<>();
        Map<PatternKey, List<Integer>> durationsByPattern = new HashMap<>();
        
        for (FestivalEvent event : events) {
            LocalDate start = event.getFstvlStart();
            LocalDate end = event.getFstvlEnd();
            
            if (start == null || end == null) continue;
            
            int month = start.getMonthValue();
            int weekOfMonth = (start.getDayOfMonth() - 1) / 7 + 1;
            DayOfWeek dayOfWeek = start.getDayOfWeek();
            
            PatternKey key = new PatternKey(month, weekOfMonth, dayOfWeek);
            patternCounts.merge(key, 1, Integer::sum);
            
            // 지속 기간 수집
            int duration = (int) ChronoUnit.DAYS.between(start, end);
            durationsByPattern.computeIfAbsent(key, k -> new ArrayList<>()).add(duration);
        }
        
        if (patternCounts.isEmpty()) {
            return PatternAnalysisResult.invalid();
        }
        
        // 가장 빈번한 패턴 찾기
        PatternKey mostFrequent = Collections.max(
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
    
    /**
     * 축제 이름 정규화
     */
    private String normalizeName(String name) {
        if (name == null) return "";
        String n = name;
        n = n.replaceAll("제\\d+회", "");
        n = n.replaceAll("\\d{4}", "");
        n = n.replaceAll("\\[예상\\]\\s*", "");
        return n.trim();
    }
    
    // 내부 클래스들
    
    private static class PatternKey {
        final int month;
        final int weekOfMonth;
        final DayOfWeek dayOfWeek;
        
        PatternKey(int month, int weekOfMonth, DayOfWeek dayOfWeek) {
            this.month = month;
            this.weekOfMonth = weekOfMonth;
            this.dayOfWeek = dayOfWeek;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PatternKey)) return false;
            PatternKey that = (PatternKey) o;
            return month == that.month &&
                   weekOfMonth == that.weekOfMonth &&
                   dayOfWeek == that.dayOfWeek;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(month, weekOfMonth, dayOfWeek);
        }
    }
    
    private static class PatternAnalysisResult {
        final boolean valid;
        final int sampleCount;
        final int month;
        final int weekOfMonth;
        final DayOfWeek dayOfWeek;
        final int durationDays;
        
        PatternAnalysisResult(boolean valid, int sampleCount, int month, 
                             int weekOfMonth, DayOfWeek dayOfWeek, int durationDays) {
            this.valid = valid;
            this.sampleCount = sampleCount;
            this.month = month;
            this.weekOfMonth = weekOfMonth;
            this.dayOfWeek = dayOfWeek;
            this.durationDays = durationDays;
        }
        
        static PatternAnalysisResult invalid() {
            return new PatternAnalysisResult(false, 0, 0, 0, null, 0);
        }
        
        boolean isValid() { return valid; }
    }
}
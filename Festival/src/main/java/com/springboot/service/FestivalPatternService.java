package com.springboot.service;

import com.springboot.domain.Festivals;
import com.springboot.repository.FestivalsRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

// 축제 이름 히스토리를 보고 "예상 개최 시기" 패턴을 뽑는 서비스

@Service
public class FestivalPatternService {

    private final FestivalsRepository repository;

    public FestivalPatternService(FestivalsRepository repository) {
        this.repository = repository;
    }

    // 결과 DTO
    public static class ExpectedPeriod {
        private final String baseName;
        private final int sampleCount;
        private final int targetYear;
        private final int month;
        private final int weekOfMonth;
        private final DayOfWeek dayOfWeek;
        private final String dayOfWeekKo;   // 한글 요일

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

    // (월, 주차, 요일) 조합 키
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

     // 축제 이름을 기준으로 과거 데이터를 모아서
     // "가장 많이 열린 (월, 주차, 요일) 패턴"을 찾고,
     // 가장 최근 연도 + 1년을 targetYear로 해서 반환.
    
    public Optional<ExpectedPeriod> predictNextYearByName(String festivalName) {

        String baseName = normalizeName(festivalName);

        // 이 이름이 들어간 모든 축제 이력
        List<Festivals> history =
                repository.findByFcltyNmContainingOrderByFstvlBeginDeAsc(baseName);

        if (history.size() < 2) {
            // 데이터가 너무 적으면 패턴 못 낸다고 판단
            return Optional.empty();
        }

        // (월, 주차, 요일) 조합별로 카운트
        Map<Key, Long> counts = new HashMap<>();

        for (Festivals f : history) {
            LocalDate d = f.getFstvlBeginDe();
            if (d == null) continue;

            int month = d.getMonthValue();
            int weekOfMonth = (d.getDayOfMonth() - 1) / 7 + 1; // 1~4(5)
            DayOfWeek dow = d.getDayOfWeek();

            Key key = new Key(month, weekOfMonth, dow);
            counts.put(key, counts.getOrDefault(key, 0L) + 1L);
        }

        if (counts.isEmpty()) {
            return Optional.empty();
        }

        // 가장 많이 나온 조합 선택
        Map.Entry<Key, Long> best = null;
        for (Map.Entry<Key, Long> e : counts.entrySet()) {
            if (best == null || e.getValue() > best.getValue()) {
                best = e;
            }
        }

        Key k = best.getKey();

        int latestYear = history.stream()
                .filter(f -> f.getFstvlBeginDe() != null)
                .map(f -> f.getFstvlBeginDe().getYear())
                .max(Integer::compareTo)
                .orElse(LocalDate.now().getYear());

        int targetYear = latestYear + 1;

        String dowKo = toKorean(k.dayOfWeek);   // 한글 요일 변환

        return Optional.of(new ExpectedPeriod(
                baseName,
                history.size(),
                targetYear,
                k.month,
                k.weekOfMonth,
                k.dayOfWeek,
                dowKo
        ));
    }

    // 축제 이름 간단 정규화 (회차, 연도 제거)
    private String normalizeName(String name) {
        if (name == null) return "";
        String n = name;
        n = n.replaceAll("제\\d+회", "");   // "제10회"
        n = n.replaceAll("\\d{4}", "");    // "2024"
        return n.trim();
    }

    // DayOfWeek -> 한글 요일 한 글자
    private String toKorean(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> "월";
            case TUESDAY   -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY  -> "목";
            case FRIDAY    -> "금";
            case SATURDAY  -> "토";
            case SUNDAY    -> "일";
        };
    }
}

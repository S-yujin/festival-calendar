package com.springboot.service;

import com.springboot.domain.FestivalEvent;
import com.springboot.repository.FestivalEventRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class FestivalPatternService {

    private final FestivalEventRepository repository;

    public FestivalPatternService(FestivalEventRepository repository) {
        this.repository = repository;
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

    public Optional<ExpectedPeriod> predictNextYearByName(String festivalName) {
        String baseName = normalizeName(festivalName);

        // FestivalEvent에서 fcltyNm으로 검색 (적절한 repository 메서드 필요)
        List<FestivalEvent> history = repository.findByFcltyNmContaining(baseName);

        if (history.size() < 2) {
            return Optional.empty();
        }

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

        if (counts.isEmpty()) {
            return Optional.empty();
        }

        Map.Entry<Key, Long> best = null;
        for (Map.Entry<Key, Long> e : counts.entrySet()) {
            if (best == null || e.getValue() > best.getValue()) {
                best = e;
            }
        }

        Key k = best.getKey();

        int latestYear = history.stream()
                .filter(f -> f.getFstvlStart() != null)
                .map(f -> f.getFstvlStart().getYear())
                .max(Integer::compareTo)
                .orElse(LocalDate.now().getYear());

        int targetYear = latestYear + 1;
        String dowKo = toKorean(k.dayOfWeek);

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
    
    public void getExpectedFestivalsForMonth(int viewYear, int month) {
        int baseYear = 2025;
        LocalDate start = LocalDate.of(baseYear, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<FestivalEvent> baseFestivals = repository.findByStartBetween(start, end, null);
    }
    
    public List<FestivalEvent> buildExpectedFestivalsForRange(LocalDate futureStart, LocalDate futureEnd) {
        if (futureStart == null || futureEnd == null || futureEnd.isBefore(futureStart)) {
            return Collections.emptyList();
        }

        int baseYear = 2025;
        int targetYear = futureStart.getYear();

        LocalDate baseStart = LocalDate.of(baseYear, futureStart.getMonthValue(), 1);
        LocalDate baseEnd = LocalDate.of(baseYear, futureEnd.getMonthValue(), 1)
                .withDayOfMonth(LocalDate.of(baseYear, futureEnd.getMonthValue(), 1).lengthOfMonth());

        List<FestivalEvent> baseFestivals = repository.findByStartBetween(baseStart, baseEnd, null);

        List<FestivalEvent> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (FestivalEvent base : baseFestivals) {
            String name = base.getFcltyNm();
            if (name == null || name.isBlank()) continue;

            Optional<ExpectedPeriod> opt = predictNextYearByName(name);
            if (opt.isEmpty()) continue;

            ExpectedPeriod ep = opt.get();

            LocalDate predictedStart = nthWeekdayOfMonth(
                    targetYear, ep.getMonth(), ep.getWeekOfMonth(), ep.getDayOfWeek()
            );

            long duration = 0;
            if (base.getFstvlStart() != null && base.getFstvlEnd() != null
                    && !base.getFstvlEnd().isBefore(base.getFstvlStart())) {
                duration = ChronoUnit.DAYS.between(base.getFstvlStart(), base.getFstvlEnd());
            }
            LocalDate predictedEnd = predictedStart.plusDays(duration);

            boolean overlaps = !predictedEnd.isBefore(futureStart) && !predictedStart.isAfter(futureEnd);
            if (!overlaps) continue;

            String key = normalizeName(name) + "|" + predictedStart;
            if (!seen.add(key)) continue;

            FestivalEvent expected = FestivalEvent.createExpected(name, predictedStart, predictedEnd);

            result.add(expected);
        }

        result.sort(Comparator.comparing(FestivalEvent::getFstvlStart,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
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
        return n.trim();
    }

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
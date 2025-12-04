package com.springboot.service;

import com.springboot.domain.Festivals;
import com.springboot.repository.FestivalsRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FestivalPatternService {

    private final FestivalsRepository repository;

    public FestivalPatternService(FestivalsRepository repository) {
        this.repository = repository;
    }

    /**
     * 2019~2024년 이력을 가지고
     * "보통 X월 Y주차 Z요일 전후 (2019~2024년 기준, 2025년에도 비슷한 시기를 예상)"
     * 이런 문장을 만들어 돌려준다. 이력이 부족하면 null.
     */
    public String estimateExpectedPeriod2025(String fcltyNm) {

        List<Festivals> all = repository.findByFcltyNm(fcltyNm);

        // 2019~2024년 사이의 시작일만 수집
        List<LocalDate> dates = all.stream()
                .map(Festivals::getFstvlBeginDe)
                .filter(Objects::nonNull)
                .filter(d -> {
                    int year = d.getYear();
                    return year >= 2019 && year <= 2024;
                })
                .toList();

        // 데이터가 너무 적으면 예상 못 한다
        if (dates.size() < 2) {
            return null;
        }

        // "월-주차-요일번호" 형태의 key로 몇 번 나왔는지 카운트
        Map<String, Long> counts = dates.stream()
                .collect(Collectors.groupingBy(d -> {
                    int month = d.getMonthValue();
                    int weekOfMonth = (d.getDayOfMonth() - 1) / 7 + 1; // 1~4/5
                    int dow = d.getDayOfWeek().getValue(); // 1=월 ~ 7=일
                    return month + "-" + weekOfMonth + "-" + dow;
                }, Collectors.counting()));

        Map.Entry<String, Long> best = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (best == null) {
            return null;
        }

        String[] parts = best.getKey().split("-");
        int month = Integer.parseInt(parts[0]);
        int weekOfMonth = Integer.parseInt(parts[1]);
        int dowVal = Integer.parseInt(parts[2]);

        String dowKo = switch (dowVal) {
            case 1 -> "월";
            case 2 -> "화";
            case 3 -> "수";
            case 4 -> "목";
            case 5 -> "금";
            case 6 -> "토";
            case 7 -> "일";
            default -> "";
        };

        // 실제로는 “2019~2024년 이력 기준으로 2025년도 비슷한 시기 예상” 이라는 의미
        return String.format(
                "보통 %d월 %d주차 %s요일 전후에 열렸습니다. " +
                "(2019~2024년 이력 기준, 2025년에도 비슷한 시기를 예상할 수 있습니다.)",
                month, weekOfMonth, dowKo
        );
    }
}

package com.springboot.tourapi;

import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalMaster;
import com.springboot.dto.TourApiDto;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
public class FestivalMatcher {
    
    private static final DateTimeFormatter API_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    /**
     * FestivalEvent와 TourAPI Item이 같은 축제인지 판별
     */
    public static boolean isSameFestival(FestivalEvent event, TourApiDto.Item api) {
        if (event == null || api == null) return false;

        FestivalMaster master = event.getMaster();
        if (master == null) return false;

        // 1) 이름 정규화해서 비교
        String dbTitle = normalize(firstNonBlank(event.getFcltyNm(), master.getFstvlNm()));
        String apiTitle = normalize(api.getTitle());

        if (dbTitle.isEmpty() || apiTitle.isEmpty()) {
            return false;
        }

        if (!dbTitle.equals(apiTitle)) {
            return false;
        }

        // 2) 날짜 겹치는지 확인 (±3일 정도 여유)
        LocalDate dbStart = event.getFstvlStart();
        LocalDate dbEnd = event.getFstvlEnd();

        LocalDate apiStart = parseDate(api.getEventstartdate());
        LocalDate apiEnd = parseDate(api.getEventenddate());

        if (dbStart != null && dbEnd != null && apiStart != null && apiEnd != null) {
            if (dbEnd.isBefore(apiStart.minusDays(3)) ||
                dbStart.isAfter(apiEnd.plusDays(3))) {
                return false;
            }
        }

        // 3) 지역(시/도 + 시/군/구) 체크
        String ctprvn = safe(master.getCtprvnNm());
        String signgu = safe(master.getSignguNm());
        String addr = safe(api.getAddr1());

        if (!ctprvn.isEmpty()) {
            String ctprvnShort = ctprvn
                    .replace("특별시", "")
                    .replace("광역시", "")
                    .replace("특별자치시", "")
                    .trim();

            if (!ctprvnShort.isEmpty() && !addr.contains(ctprvnShort)) {
                return false;
            }
        }

        if (!signgu.isEmpty() && !addr.contains(signgu)) {
            return false;
        }

        return true;
    }

    /** 축제명 정규화: 연도/회차/공백/특수문자 제거 */
    private static String normalize(String s) {
        if (s == null) return "";
        return s
                .replaceAll("\\d{4}", "")
                .replaceAll("제\\s*\\d+회", "")
                .replaceAll("\\s+", "")
                .replaceAll("[\\p{Punct}]", "")
                .toLowerCase();
    }

    private static LocalDate parseDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) return null;
        try {
            return LocalDate.parse(yyyymmdd, API_DATE_FMT);
        } catch (Exception e) {
            log.debug("TourAPI 날짜 파싱 실패: {}", yyyymmdd);
            return null;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
package com.springboot.tourapi;

import com.springboot.domain.Festivals;
import com.springboot.dto.TourApiDto;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
public class FestivalMatcher {

    private static final DateTimeFormatter API_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 우리 DB의 Festivals 한 건과 TourAPI Item 한 건이
     * 같은 축제인지 판별하는 로직
     *
     * - 축제명(정규화 후) 일치
     * - 개최기간이 어느 정도 겹치는지
     * - 시/도 + 시/군/구가 주소에 포함되는지
     */
    public static boolean isSameFestival(Festivals f, TourApiDto.Item api) {
        if (f == null || api == null) return false;

        // 1) 이름 정규화해서 비교
        String dbTitle = normalize(f.getFstvlNm());
        String apiTitle = normalize(api.getTitle());

        if (dbTitle.isEmpty() || apiTitle.isEmpty()) {
            return false;
        }

        if (!dbTitle.equals(apiTitle)) {
            return false;
        }

        // 2) 날짜 겹치는지 확인 (±3일 정도 여유)
        LocalDate dbStart = f.getFstvlBeginDe();
        LocalDate dbEnd   = f.getFstvlEndDe();

        LocalDate apiStart = parseDate(api.getEventstartdate());
        LocalDate apiEnd   = parseDate(api.getEventenddate());

        if (dbStart != null && dbEnd != null && apiStart != null && apiEnd != null) {

            // 기간이 완전 안 겹치면 다른 축제로 판단
            if (dbEnd.isBefore(apiStart.minusDays(3)) ||
                dbStart.isAfter(apiEnd.plusDays(3))) {
                return false;
            }
        }

        // 3) 지역(시/도 + 시/군/구) 체크
        String ctprvn = safe(f.getCtprvnNm()); // 예: "부산광역시"
        String signgu = safe(f.getSignguNm()); // 예: "중구"

        String addr = safe(api.getAddr1());    // 예: "부산광역시 중구 광복로 88..."

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
                .replaceAll("\\d{4}", "")          // 2025 같은 연도
                .replaceAll("제\\s*\\d+회", "")     // 제 5회
                .replaceAll("\\s+", "")            // 공백
                .replaceAll("[\\p{Punct}]", "")    // 특수문자
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

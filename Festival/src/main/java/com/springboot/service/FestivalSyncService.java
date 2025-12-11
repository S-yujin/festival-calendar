package com.springboot.service;

import com.springboot.domain.Festivals;
import com.springboot.repository.FestivalsRepository;
import com.springboot.tourapi.FestivalMatcher;
import com.springboot.tourapi.TourApiClient;
import com.springboot.dto.TourApiDto.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalSyncService {

    private final FestivalsRepository festivalRepository;
    private final TourApiClient tourApiClient;

    /**
     * 2025년 축제에 대해 TourAPI 정보(대표사진/좌표 등) 동기화
     * - DB: 2025년 축제만 조회
     * - TourAPI: 전국 2025년 축제 전체 조회
     * - 비어있는 필드만 TourAPI 데이터로 채움
     */
    @Transactional
    public void sync2025Festivals() {

        // 1. 우리 DB에서 2025년에 열리는 축제들만 가져오기
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end   = LocalDate.of(2025, 12, 31);

        List<Festivals> dbFestivals =
                festivalRepository.findByFstvlBeginDeBetween(start, end);

        log.info("DB 2025년 축제 수: {}", dbFestivals.size());

        // 2. TourAPI에서 2025년 축제 목록 전체 가져오기 (전국)
        //    → TourApiClient 쪽에서 areacode/sigunguCode는 null 로 처리
        List<Item> apiFestivals = tourApiClient.fetchFestivals2025(null, null);
        log.info("TourAPI 2025년 축제 수: {}",
                apiFestivals != null ? apiFestivals.size() : 0);

        if (apiFestivals == null || apiFestivals.isEmpty()) {
            log.warn("TourAPI에서 2025년 축제 목록을 가져오지 못했습니다. 동기화를 건너뜁니다.");
            return;
        }

        // 3. 매칭 + 업데이트
        for (Festivals f : dbFestivals) {

            Item matched = apiFestivals.stream()
                    .filter(item -> FestivalMatcher.isSameFestival(f, item))
                    .findFirst()
                    .orElse(null);

            if (matched == null) {
                // 어떤 축제가 매칭 안 되는지 보려고 우리 쪽 정보 위주로 찍기
                log.warn("TourAPI 매칭 실패 - 축제명: {}, 시작일: {}, 종료일: {}, 주소: {}",
                        f.getFstvlNm(),
                        f.getFstvlBeginDe(),
                        f.getFstvlEndDe(),
                        f.getAddr1());
                continue;
            }

            // ====== 비어 있는 필드만 TourAPI 값으로 채우기 ======

            // 1) contentId
            if (f.getTourapiContentId() == null) {
                f.setTourapiContentId(matched.getContentid());
            }

            // 2) 대표 이미지
            if (isBlank(f.getFirstImageUrl()) && !isBlank(matched.getFirstimage())) {
                f.setFirstImageUrl(matched.getFirstimage());
            }

            // 3) 주소
            if (isBlank(f.getAddr1()) && !isBlank(matched.getAddr1())) {
                f.setAddr1(matched.getAddr1());
            }

            // 4) mapx/mapy (문자열로 저장하고 있는 필드라면 그대로)
            if (isBlank(f.getMapX()) && !isBlank(matched.getMapx())) {
                f.setMapX(matched.getMapx());
            }
            if (isBlank(f.getMapY()) && !isBlank(matched.getMapy())) {
                f.setMapY(matched.getMapy());
            }

            // 5) 지도에서 쓰는 위도/경도(fcltyLa / fcltyLo) – 둘 다 비어 있을 때만 세팅
            if (f.getFcltyLa() == null && f.getFcltyLo() == null &&
                    matched.getMapx() != null && matched.getMapy() != null) {
                try {
                    // TourAPI: mapy = 위도, mapx = 경도
                    f.setFcltyLa(Double.parseDouble(matched.getMapy()));
                    f.setFcltyLo(Double.parseDouble(matched.getMapx()));
                } catch (NumberFormatException e) {
                    log.warn("위경도 파싱 실패 contentId={}", matched.getContentid(), e);
                }
            }

            // 동기화 완료 플래그
            f.setDetailLoaded(true);

            log.info("TourAPI 매칭 성공: {} -> contentId={}",
                    f.getFstvlNm(), matched.getContentid());
        }

        // @Transactional 이라서 자동 flush
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
package com.springboot.init;

import com.springboot.domain.Festivals;
import com.springboot.repository.FestivalsRepository;
import com.springboot.service.TourApiService;
import com.springboot.tourapi.TourApiFestivalInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// 앱 실행 시 기존 Festivals 데이터에 대해
// TourAPI에서 이미지/설명/좌표를 가져와 채워 넣는 로더

@Slf4j
@Component
@RequiredArgsConstructor
public class FestivalTourApiLoader implements CommandLineRunner {

    private final FestivalsRepository festivalsRepository;
    private final TourApiService tourApiService;

    @Override
    public void run(String... args) {
        log.info("=== FestivalTourApiLoader 시작 ===");

        List<Festivals> festivals = festivalsRepository.findAll();

        int maxApiCalls = 100;   // ⚠️ 한 번 실행할 때 TourAPI 최대 호출 수
        int apiCalls = 0;

        for (Festivals f : festivals) {

            // 이미 정보가 있으면 스킵 (여러 번 실행돼도 안전하게)
            if (f.getOverview() != null && f.getFirstImageUrl() != null) {
                continue;
            }

            String name = f.getFcltyNm(); // 축제명 필드
            if (name == null || name.isBlank()) {
                continue;
            }

            // 더 이상 호출하면 안 되는 시점이면 루프 종료
            if (apiCalls >= maxApiCalls) {
                log.warn("TourAPI 최대 호출 수({})에 도달해서 더 이상 요청하지 않습니다.", maxApiCalls);
                break;
            }

            apiCalls++;   // 실제로 TourAPI 한 번 호출할 거니까 카운트 증가

            tourApiService.fetchFestivalInfo(name).ifPresent(info -> {
                log.info("TourAPI 정보 매핑: festivalId={} name={}", f.getId(), name);

                if (f.getOverview() == null) {
                    f.setOverview(info.getOverview());
                }
                if (f.getFirstImageUrl() == null) {
                    f.setFirstImageUrl(info.getFirstImageUrl());
                }
                if (f.getAddr1() == null) {
                    f.setAddr1(info.getAddr1());
                }
                if (f.getMapX() == null) {
                    f.setMapX(info.getMapX());
                }
                if (f.getMapY() == null) {
                    f.setMapY(info.getMapY());
                }

                festivalsRepository.save(f);
            });

            // 필요하면 딜레이도 추가 가능
            // try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        log.info("=== FestivalTourApiLoader 종료 (총 TourAPI 호출 수: {}) ===", apiCalls);
    }
}

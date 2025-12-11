package com.springboot.init;

import com.springboot.service.FestivalSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FestivalTourApiLoader implements CommandLineRunner {

    private final FestivalSyncService festivalSyncService;

    @Override
    public void run(String... args) {
        log.info("=== 2025년 TourAPI 축제 동기화 시작 ===");
        try {
            festivalSyncService.sync2025Festivals();
        } catch (Exception e) {
            log.error("2025년 TourAPI 축제 동기화 중 오류", e);
        }
        log.info("=== 2025년 TourAPI 축제 동기화 종료 ===");
    }
}
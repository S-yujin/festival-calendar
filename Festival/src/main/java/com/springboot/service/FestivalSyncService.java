package com.springboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalMaster;
import com.springboot.dto.TourApiDto;
import com.springboot.repository.FestivalEventRepository;
import com.springboot.repository.FestivalMasterRepository;
import com.springboot.tourapi.TourApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalSyncService {

    private final TourApiClient tourApiClient;
    private final FestivalEventRepository eventRepository;
    private final FestivalMasterRepository masterRepository;

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 2025년 축제 데이터를 TourAPI에서 가져와서 동기화
     */
    public void sync2025Festivals() {
        log.info("=== 2025년 축제 동기화 시작 ===");
        
        List<TourApiDto.Item> festivals = tourApiClient.fetchFestivals2025(null, null);
        log.info("TourAPI에서 가져온 축제 수: {}", festivals.size());
        
        int created = 0;
        int updated = 0;
        int failed = 0;
        
        for (TourApiDto.Item item : festivals) {
            try {
                boolean isNew = syncSingleFestival(item);
                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("축제 동기화 실패: contentId={}, title={}, error={}", 
                    item.getContentid(), item.getTitle(), e.getMessage());
            }
        }
        
        log.info("=== 2025년 축제 동기화 완료: 생성={}, 업데이트={}, 실패={} ===", 
            created, updated, failed);
    }

    /**
     * 단일 축제 동기화
     */
    @Transactional
    public boolean syncSingleFestival(TourApiDto.Item item) {
        FestivalMaster master = findOrCreateMaster(item);
        if (master == null) {
            throw new IllegalStateException("Master 생성 실패");
        }
        
        return createOrUpdateEvent(master, item);
    }

    /**
     * 특정 연도의 축제 상세 정보(overview)를 TourAPI에서 가져와서 업데이트
     */
    @Transactional
    public void syncTourApiForYear(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        List<FestivalEvent> events = eventRepository.findOverlapping(start, end);
        log.info("[TourAPI Sync] year={} 대상 이벤트 수={}", year, events.size());

        int updated = 0;

        for (FestivalEvent e : events) {
            FestivalMaster m = e.getMaster();
            if (m == null) continue;

            if (Boolean.TRUE.equals(m.getDetailLoaded())) continue;

            Long contentId = m.getTourApiContentId();
            if (contentId == null) continue;

            try {
                String overview = tourApiClient.fetchOverview(String.valueOf(contentId));
                if (overview != null && !overview.isBlank()) {
                    m.setOverview(overview);
                    m.setDetailLoaded(true);
                    masterRepository.save(m);
                    updated++;
                }
            } catch (Exception ex) {
                log.warn("[TourAPI Sync] 상세 정보 조회 실패: contentId={}", contentId, ex);
            }
        }

        log.info("[TourAPI Sync] year={} 업데이트된 master 수={}", year, updated);
    }

    /**
     * 특정 연도 축제의 상세 이미지들을 수집
     */
    @Transactional
    public void syncImagesForYear(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        List<FestivalEvent> events = eventRepository.findOverlapping(start, end);
        log.info("[Image Sync] year={} 대상 이벤트 수={}", year, events.size());

        int updated = 0;
        int skipped = 0;

        for (FestivalEvent e : events) {
            FestivalMaster m = e.getMaster();
            if (m == null) continue;

            // 이미 이미지를 수집했으면 스킵
            if (m.getImageUrls() != null && !m.getImageUrls().isEmpty()) {
                skipped++;
                continue;
            }

            Long contentId = m.getTourApiContentId();
            if (contentId == null) continue;

            try {
                List<String> images = tourApiClient.fetchDetailImages(String.valueOf(contentId));
                
                if (!images.isEmpty()) {
                    // JSON 배열로 저장
                    ObjectMapper mapper = new ObjectMapper();
                    m.setImageUrls(mapper.writeValueAsString(images));
                    
                    // 첫 번째 이미지를 originalImageUrl로도 저장
                    if (m.getOriginalImageUrl() == null || m.getOriginalImageUrl().isEmpty()) {
                        m.setOriginalImageUrl(images.get(0));
                    }
                    
                    masterRepository.save(m);
                    updated++;
                    
                    log.info("이미지 수집 완료: contentId={}, count={}", contentId, images.size());
                }
                
                // API 호출 제한 방지 (0.1초 대기)
                Thread.sleep(100);
                
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("이미지 수집 중단됨");
                break;
            } catch (Exception ex) {
                log.warn("이미지 수집 실패: contentId={}", contentId, ex);
            }
        }

        log.info("[Image Sync] year={} 완료: 업데이트={}, 스킵={}", year, updated, skipped);
    }

    private FestivalMaster findOrCreateMaster(TourApiDto.Item item) {
        String contentId = item.getContentid();
        if (contentId == null || contentId.isBlank()) {
            return null;
        }
        
        Long contentIdLong;
        try {
            contentIdLong = Long.parseLong(contentId);
        } catch (NumberFormatException e) {
            log.warn("Invalid contentId: {}", contentId);
            return null;
        }
        
        FestivalMaster master = masterRepository.findByTourApiContentId(contentIdLong)
            .orElse(null);
        
        if (master == null) {
            master = new FestivalMaster();
            master.setTourApiContentId(contentIdLong);
        }
        
        // 기본 정보 업데이트
        master.setFstvlNm(item.getTitle());
        master.setAddr1(item.getAddr1());
        master.setFirstImageUrl(item.getFirstimage());
        
        // firstimage2 저장
        if (item.getFirstimage2() != null && !item.getFirstimage2().isBlank()) {
            master.setFirstImageUrl2(item.getFirstimage2());
        }
        
        // 좌표 정보
        if (item.getMapx() != null && !item.getMapx().isBlank()) {
            master.setMapX(parseDouble(item.getMapx()));
        }
        if (item.getMapy() != null && !item.getMapy().isBlank()) {
            master.setMapY(parseDouble(item.getMapy()));
        }
        
        return masterRepository.save(master);
    }

    private boolean createOrUpdateEvent(FestivalMaster master, TourApiDto.Item item) {
        if (master == null || master.getId() == null) {
            log.warn("Master ID가 null입니다. contentId={}", item.getContentid());
            return false;
        }
        
        LocalDate startDate = parseYyyymmdd(item.getEventstartdate());
        LocalDate endDate = parseYyyymmdd(item.getEventenddate());
        
        if (startDate == null || endDate == null) {
            log.debug("날짜 파싱 실패: contentId={}, start={}, end={}", 
                item.getContentid(), item.getEventstartdate(), item.getEventenddate());
            return false;
        }
        
        List<FestivalEvent> existingEvents = eventRepository
            .findByMasterAndFstvlStartAndFstvlEnd(master, startDate, endDate);
        
        FestivalEvent event;
        boolean isNew;
        
        if (existingEvents.isEmpty()) {
            event = FestivalEvent.create(master, startDate, endDate);
            isNew = true;
        } else {
            event = existingEvents.get(0);
            isNew = false;
        }
        
        event.setFcltyNm(item.getTitle());
        event.setRawId(item.getContentid());
        
        eventRepository.save(event);
        return isNew;
    }

    private LocalDate parseYyyymmdd(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) return null;
        try {
            return LocalDate.parse(yyyymmdd.trim(), YYYYMMDD);
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
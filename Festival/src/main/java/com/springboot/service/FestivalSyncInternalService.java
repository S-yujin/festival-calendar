package com.springboot.service;

import com.springboot.domain.FestivalEvent;
import com.springboot.domain.FestivalMaster;
import com.springboot.dto.TourApiDto;
import com.springboot.repository.FestivalEventRepository;
import com.springboot.repository.FestivalMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 내부 트랜잭션 처리를 위한 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalSyncInternalService {

    private final FestivalEventRepository eventRepository;
    private final FestivalMasterRepository masterRepository;

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 단일 축제 동기화 (새로운 독립 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean syncSingleFestival(TourApiDto.Item item) {
        // Master 데이터 찾기 또는 생성
        FestivalMaster master = findOrCreateMaster(item);
        if (master == null) {
            throw new IllegalStateException("Master 생성 실패");
        }
        
        // Event 데이터 생성 또는 업데이트
        return createOrUpdateEvent(master, item);
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
        
        // contentId로 기존 Master 찾기
        FestivalMaster master = masterRepository.findByTourApiContentId(contentIdLong)
            .orElse(null);
        
        if (master == null) {
            // 새로 생성
            master = new FestivalMaster();
            master.setTourApiContentId(contentIdLong);
        }
        
        // 데이터 업데이트
        master.setFstvlNm(item.getTitle());
        master.setAddr1(item.getAddr1());
        master.setFirstImageUrl(item.getFirstimage());
        
        if (item.getMapx() != null && !item.getMapx().isBlank()) {
            master.setMapX(parseDouble(item.getMapx()));
        }
        if (item.getMapy() != null && !item.getMapy().isBlank()) {
            master.setMapY(parseDouble(item.getMapy()));
        }
        
        master = masterRepository.save(master);
        return master;
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
        
        // 같은 master, startDate, endDate를 가진 Event 찾기
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
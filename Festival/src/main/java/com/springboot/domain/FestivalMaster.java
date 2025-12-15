package com.springboot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "festival_master")
public class FestivalMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fstvl_nm")
    private String fstvlNm;

    @Column(name = "ctprvn_nm")
    private String ctprvnNm;

    @Column(name = "signgu_nm")
    private String signguNm;

    @Column(name = "legaldong_nm")
    private String legaldongNm;

    @Column(name = "adstrd_nm")
    private String adstrdNm;

    @Column(name = "zip_no")
    private String zipNo;

    @Column(name = "addr1")
    private String addr1;

    @Column(name = "tel_no")
    private String telNo;

    @Column(name = "hmpg_addr")
    private String hmpgAddr;

    @Column(name = "mapx")
    private Double mapX;

    @Column(name = "mapy")
    private Double mapY;

    @Column(name = "first_image_url", length = 500)
    private String firstImageUrl;

    @Column(name = "first_image_url2", length = 500)
    private String firstImageUrl2;
    
    @Column(name = "original_image_url", length = 500)
    private String originalImageUrl;

    @Lob
    @Column(name = "image_urls")
    private String imageUrls;

    @Lob
    @Column(name = "overview")
    private String overview;

    @Column(name = "tourapi_content_id")
    private Long tourApiContentId;

    @Column(name = "detail_loaded")
    private Boolean detailLoaded = false;
    
    @Column(name = "image_locked")
    private Boolean imageLocked = false;

    // ===== 개최 패턴 분석 필드 (내부 사용) =====
    
    /**
     * 분석된 샘플 수
     */
    @Column(name = "pattern_sample_count")
    private Integer patternSampleCount;

    /**
     * 예상 개최 월 (1~12)
     */
    @Column(name = "expected_month")
    private Integer expectedMonth;

    /**
     * 예상 개최 주차 (1~5)
     */
    @Column(name = "expected_week_of_month")
    private Integer expectedWeekOfMonth;

    /**
     * 예상 개최 요일
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "expected_day_of_week")
    private DayOfWeek expectedDayOfWeek;

    /**
     * 예상 지속 기간 (일 단위)
     */
    @Column(name = "expected_duration_days")
    private Integer expectedDurationDays;

    /**
     * 패턴 마지막 업데이트 시각
     */
    @Column(name = "pattern_last_updated")
    private LocalDateTime patternLastUpdated;

    /**
     * 패턴 데이터 존재 여부 확인
     */
    public boolean hasPattern() {
        return patternSampleCount != null 
            && patternSampleCount >= 2
            && expectedMonth != null 
            && expectedWeekOfMonth != null 
            && expectedDayOfWeek != null;
    }
}
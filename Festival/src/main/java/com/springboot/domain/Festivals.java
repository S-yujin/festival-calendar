package com.springboot.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자 자동 생성
@AllArgsConstructor                              // 모든 필드 받는 생성자
@Entity
@Data
@Table(name = "festival")
public class Festivals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;       // AUTO_INCREMENT PK

    @Column(name = "raw_id")
    private String rawId;

    @Column(name = "lclas_nm")
    private String lclasNm;

    @Column(name = "mlsfc_nm")
    private String mlsfcNm;

    @Column(name = "fclty_nm")
    private String fcltyNm;

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

    @Column(name = "fclty_lo")
    private Double fcltyLo;

    @Column(name = "fclty_la")
    private Double fcltyLa;

    @Column(name = "fstvl_begin_de")
    private LocalDate fstvlBeginDe;

    @Column(name = "fstvl_end_de")
    private LocalDate fstvlEndDe;

    @Column(name = "fstvl_cn", columnDefinition = "TEXT")
    private String fstvlCn;

    @Column(name = "tel_no")
    private String telNo;

    @Column(name = "hmpg_addr")
    private String hmpgAddr;

    @Column(name = "data_base_de")
    private LocalDate dataBaseDe;

    @Column(name = "origin_nm")
    private String originNm;
    
    // TourAPI에서 채우는 축제 상세 설명
    @Lob
    @Column(columnDefinition = "TEXT")
    private String overview;

    // TourAPI에서 채우는 대표 이미지 URL 
    private String firstImageUrl;

    // TourAPI에서 채우는 주소
    private String addr1;
    // 축제명
    private String fstvlNm; 

    // 위도 / 경도 (문자열로 저장)
    private String mapY;  // 위도
    private String mapX;  // 경도
    
    // TourAPI contentId 저장용
    @Column(name = "tourapi_content_id")
    private String tourapiContentId;
    
    @Column(name = "detail_loaded")
    // TourAPI 상세 정보까지 채웠는지 여부
    private Boolean detailLoaded = false;
}

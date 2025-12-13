package com.springboot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    private String firstImageUrl2;  // 썸네일 이미지
    
    @Column(name = "original_image_url", length = 500)
    private String originalImageUrl;  // 원본 고화질 이미지

    @Lob
    @Column(name = "image_urls")
    private String imageUrls;  // JSON 배열: ["url1", "url2", ...]

    @Lob
    @Column(name = "overview")
    private String overview;

    @Column(name = "tourapi_content_id")
    private Long tourApiContentId;

    @Column(name = "detail_loaded")
    private Boolean detailLoaded = false;
}
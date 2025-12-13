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

    @Lob
    @Column(name = "overview")
    private String overview;

    @Column(name = "tourapi_content_id")
    private Long tourApiContentId;

    // FestivalSyncService에서 사용하는 필드 추가
    @Column(name = "detail_loaded")
    private Boolean detailLoaded = false;
}
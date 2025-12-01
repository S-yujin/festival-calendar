package com.springboot.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자 자동 생성
@AllArgsConstructor                              // 모든 필드 받는 생성자
@Entity
@Table(name = "festival_2024")
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
}

package com.springboot.tourapi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TourAPI에서 가져온 축제 상세 정보를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TourApiFestivalInfo {

    private String title;         // 이름
    private String overview;      // 상세 설명
    private String firstImageUrl; // 대표 이미지
    private String addr1;         // 주소
    private String mapX;          // 경도
    private String mapY;          // 위도
}

package com.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FestivalMarker {
	private Long id;
	private String name;
	private Double lat; // 위도
	private Double lng; // 경도
}

package com.springboot.dto;

import lombok.Data;

@Data
public class TourApiEventDto {
    private String contentid;
    private String title;
    private String addr1;
    private String eventstartdate;
    private String eventenddate;
}

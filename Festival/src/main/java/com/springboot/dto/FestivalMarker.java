package com.springboot.dto;

public class FestivalMarker {

    private Long id;
    private String name;
    private Double lat;
    private Double lng;
    private String status; // "ONGOING", "UPCOMING", "PAST"

    public FestivalMarker(Long id, String name, Double lat, Double lng, String status) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public String getStatus() {
        return status;
    }
}

package com.springboot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "festival_event",
       indexes = {
           @Index(name = "idx_event_dates", columnList = "fstvl_start,fstvl_end"),
           @Index(name = "idx_event_master", columnList = "master_id")
       })
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FestivalEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)  // optional = false 제거
    @JoinColumn(name = "master_id", nullable = true)  // nullable 명시
    private FestivalMaster master;

    @Column(name = "fstvl_start")
    private LocalDate fstvlStart;

    @Column(name = "fstvl_end")
    private LocalDate fstvlEnd;

    @Column(name = "raw_id")
    private String rawId;

    @Column(name = "data_base_de")
    private LocalDate dataBaseDe;

    @Column(name = "origin_nm")
    private String originNm;

    @Column(name = "fclty_nm")
    private String fcltyNm;
    
    public static FestivalEvent createExpected(String name, LocalDate start, LocalDate end) {
        FestivalEvent event = new FestivalEvent();
        event.setFcltyNm("[예상] " + name);
        event.setFstvlStart(start);
        event.setFstvlEnd(end);
        return event;
    }
    
    public static FestivalEvent create(FestivalMaster master, LocalDate start, LocalDate end) {
        FestivalEvent event = new FestivalEvent();
        event.setMaster(master);
        event.setFstvlStart(start);
        event.setFstvlEnd(end);
        return event;
    }
}
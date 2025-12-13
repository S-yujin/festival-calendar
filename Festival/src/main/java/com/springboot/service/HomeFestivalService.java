package com.springboot.service;

import com.springboot.domain.FestivalEvent;
import com.springboot.repository.FestivalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeFestivalService {

    private final FestivalEventRepository eventRepository;

    public List<FestivalEvent> getTodayOngoing(int limit) {
        LocalDate today = LocalDate.now();
        return eventRepository.findOngoing(today, PageRequest.of(0, limit));
    }

    public List<FestivalEvent> getThisMonth(int limit) {
        YearMonth ym = YearMonth.now();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return eventRepository.findByStartBetween(start, end, PageRequest.of(0, limit));
    }
}

package com.springboot.controller;

import com.springboot.domain.festivales;
import com.springboot.repository.FestivalesRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/festivals")   // 공통 prefix
public class FestivalesController {

    private final FestivalesRepository repository;

    public FestivalesController(FestivalesRepository repository) {
        this.repository = repository;
    }

    // 간단 테스트용 (옵션)
    @GetMapping("/test")
    public String test(Model model) {
        long count = repository.count();
        model.addAttribute("count", count);
        return "test";  // templates/test.html 있으면 사용, 없으면 @ResponseBody로 바꿔도 됨
    }

    // 2024 전체 목록
    @GetMapping("/2024")
    public String list2024(Model model) {
        List<festivales> list = repository.findAll();
        model.addAttribute("festivals", list);
        return "list2024";       // templates/list2024.html
    }

    // 연/월별 캘린더
    @GetMapping("/2024/calendar")
    public String calendar2024(
            @RequestParam(name = "year",  defaultValue = "2024") int year,
            @RequestParam(name = "month", defaultValue = "10")   int month,
            Model model
    ) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<festivales> list = repository.findByFstvlBeginDeBetween(start, end);

        model.addAttribute("festivals", list);
        model.addAttribute("year", year);
        model.addAttribute("month", month);

        return "calendar2024";   // templates/calendar2024.html
    }
}

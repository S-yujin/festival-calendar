package com.springboot.dto;

import com.springboot.domain.Bookmark;
import com.springboot.domain.FestivalMaster;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class BookmarkResponse {
    private Long bookmarkId;
    private Long festivalId;
    private String festivalName;
    private String imageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private LocalDateTime createdAt;

    public static BookmarkResponse from(Bookmark bookmark) {
        FestivalMaster master = bookmark.getEvent().getMaster();
        
        String festivalName = bookmark.getEvent().getFcltyNm();
        if (master != null && master.getFstvlNm() != null) {
            festivalName = master.getFstvlNm();
        }
        
        String imageUrl = null;
        if (master != null) {
            imageUrl = master.getFirstImageUrl2() != null 
                ? master.getFirstImageUrl2() 
                : master.getFirstImageUrl();
        }
        
        String location = "";
        if (master != null) {
            String ctprvn = master.getCtprvnNm() != null ? master.getCtprvnNm() : "";
            String signgu = master.getSignguNm() != null ? master.getSignguNm() : "";
            location = (ctprvn + " " + signgu).trim();
        }
        
        return new BookmarkResponse(
            bookmark.getId(),
            bookmark.getEvent().getId(),
            festivalName,
            imageUrl,
            bookmark.getEvent().getFstvlStart(),
            bookmark.getEvent().getFstvlEnd(),
            location,
            bookmark.getCreatedAt()
        );
    }
}
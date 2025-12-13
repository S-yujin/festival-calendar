package com.springboot.tourapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.dto.TourApiDto;
import com.springboot.dto.TourApiDto.Item;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiClient {

    private final RestTemplate restTemplate;

    // 스프링 빈 주입이 아니라, 그냥 직접 생성해서 사용
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tourapi.service-key}")
    private String serviceKey;

    @Value("${tourapi.base-url:https://apis.data.go.kr/B551011/KorService2}")
    private String baseUrl;

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "festival-project";
    
    public String fetchOverview(String contentId) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(baseUrl + "/detailCommon2")
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("contentId", contentId)
                    .queryParam("overviewYN", "Y")
                    .queryParam("defaultYN", "Y")
                    .queryParam("_type", "json")
                    .build(true)
                    .toUriString();

            String rawJson = restTemplate.getForObject(url, String.class);

            var root = objectMapper.readTree(rawJson);
            var item0 = root.path("response").path("body").path("items").path("item");
            if (item0.isArray() && item0.size() > 0) item0 = item0.get(0);

            String overview = item0.path("overview").asText(null);
            return (overview == null || overview.isBlank()) ? null : overview;

        } catch (Exception e) {
            log.warn("detailCommon2 overview 조회 실패 contentId={}", contentId, e);
            return null;
        }
    }
    
    /**
     * 2025년 특정 지역(옵션)에 대해 contentTypeId=15 축제 목록 전체 가져오기
     */
    public List<Item> fetchFestivals2025(String areaCode, String sigunguCode) {
        List<Item> result = new ArrayList<>();

        int pageNo = 1;
        int numOfRows = 100; // API 최대치에 맞춰서 조정

        while (true) {
            String url = buildFestivalListUrl(areaCode, sigunguCode, pageNo, numOfRows);
            log.info("TourAPI festival list URL = {}", url);

            // 1. 응답을 일단 String(raw JSON)으로 받기
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
            String rawJson = responseEntity.getBody();

            if (pageNo == 1) {
                log.info("TourAPI raw response (pageNo={}): {}", pageNo, rawJson);
            }

            // 2. JSON -> DTO 매핑
            TourApiDto response;
            try {
                response = objectMapper.readValue(rawJson, TourApiDto.class);
            } catch (Exception e) {
                log.error("TourAPI JSON 파싱 오류 pageNo={}, msg={}", pageNo, e.getMessage(), e);
                break;
            }

            if (response == null ||
                response.getResponse() == null ||
                response.getResponse().getHeader() == null) {

                log.warn("TourAPI festival list 응답 비정상(pageNo={}) response or header null", pageNo);
                log.warn("raw response = {}", rawJson);
                break;
            }

            var header = response.getResponse().getHeader();
            log.info("TourAPI header pageNo={} resultCode={}, resultMsg={}",
                    pageNo, header.getResultCode(), header.getResultMsg());

            if (!"0000".equals(header.getResultCode())) {
                log.warn("TourAPI 오류 resultCode={}, resultMsg={}",
                        header.getResultCode(), header.getResultMsg());
                log.warn("raw response = {}", rawJson);
                break;
            }

            if (response.getResponse().getBody() == null ||
                response.getResponse().getBody().getItems() == null ||
                response.getResponse().getBody().getItems().getItem() == null) {

                log.warn("TourAPI festival list 응답 비정상(pageNo={}) body/items/item null", pageNo);
                log.warn("raw response = {}", rawJson);
                break;
            }

            var body = response.getResponse().getBody();
            List<Item> items = body.getItems().getItem();

            int totalCount = body.getTotalCount();
            log.info("TourAPI body pageNo={} totalCount={}, itemsInThisPage={}",
                    pageNo, totalCount, items.size());

            if (!items.isEmpty()) {
                Item sample = items.get(0);
                log.info("TourAPI sample item pageNo={} : contentid={}, title={}",
                        pageNo, sample.getContentid(), sample.getTitle());
            }

            if (items.isEmpty()) {
                log.info("TourAPI festival list item 0건 pageNo={}", pageNo);
                break;
            }

            result.addAll(items);

            int lastPage = (int) Math.ceil((double) totalCount / numOfRows);
            if (pageNo >= lastPage) {
                log.info("TourAPI festival list 마지막 페이지 도달 pageNo={} / lastPage={}", pageNo, lastPage);
                break;
            }

            pageNo++;
        }

        log.info("TourAPI festival list 전체 수집 완료: {}건", result.size());
        return result;
    }

    private String buildFestivalListUrl(String areaCode, String sigunguCode,
                                        int pageNo, int numOfRows) {

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl + "/searchFestival2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", pageNo)
                // .queryParam("listYN", "Y")
                .queryParam("arrange", "A")
                .queryParam("eventStartDate", "20250101")
                .queryParam("eventEndDate", "20251231")
                .queryParam("_type", "json");

        if (areaCode != null && !areaCode.isBlank()) {
            builder.queryParam("areaCode", areaCode);
        }
        if (sigunguCode != null && !sigunguCode.isBlank()) {
            builder.queryParam("sigunguCode", sigunguCode);
        }

        return builder.toUriString();
    }
    
    // detailImage1 API로 축제의 추가 이미지 목록 가져오기
    public List<String> fetchDetailImages(String contentId) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(baseUrl + "/detailImage1")
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("contentId", contentId)
                    .queryParam("MobileOS", MOBILE_OS)
                    .queryParam("MobileApp", MOBILE_APP)
                    .queryParam("imageYN", "Y")
                    .queryParam("subImageYN", "Y")
                    .queryParam("numOfRows", "20")
                    .queryParam("_type", "json")
                    .build(true)
                    .toUriString();

            log.debug("fetchDetailImages URL: {}", url);
            
            String rawJson = restTemplate.getForObject(url, String.class);
            
            var root = objectMapper.readTree(rawJson);
            var itemsNode = root.path("response").path("body").path("items").path("item");
            
            List<String> imageUrls = new ArrayList<>();
            
            if (itemsNode.isArray()) {
                for (var item : itemsNode) {
                    String originUrl = item.path("originimgurl").asText(null);
                    if (originUrl != null && !originUrl.isBlank()) {
                        imageUrls.add(originUrl);
                        log.debug("Image found: {}", originUrl);
                    }
                }
            } else if (!itemsNode.isMissingNode()) {
                // 단일 이미지인 경우
                String originUrl = itemsNode.path("originimgurl").asText(null);
                if (originUrl != null && !originUrl.isBlank()) {
                    imageUrls.add(originUrl);
                }
            }
            
            log.info("contentId={} 이미지 {}개 수집", contentId, imageUrls.size());
            return imageUrls;
            
        } catch (Exception e) {
            log.warn("detailImage1 조회 실패 contentId={}", contentId, e);
            return new ArrayList<>();
        }
    }
}

package com.springboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.tourapi.TourApiFestivalInfo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Service
public class TourApiService {

    @Value("${tourapi.service-key}")
    private String serviceKey;   // application.yml 에는 보통 디코딩된 키 넣어둠

    @Value("${tourapi.base-url}")
    private String baseUrl;      // 예: https://apis.data.go.kr/B551011/KorService2

    @Value("${tourapi.app-name}")
    private String appName;      // 예: festival-project

    // JSON 파싱용
    private final ObjectMapper objectMapper = new ObjectMapper();
    // HTTP 호출용
    private final RestTemplate restTemplate = new RestTemplate();

    // 한 번이라도 429 나면 true로 바뀌고, 이후 호출은 바로 스킵
    private volatile boolean quotaExceeded = false;

    /**
     * 축제 이름(키워드)으로 TourAPI 조회해서
     * 이미지/설명/좌표 정보를 Optional 로 반환
     */
    public Optional<TourApiFestivalInfo> fetchFestivalInfo(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        // 이미 한도 초과 확인된 상태면 바로 포기
        if (quotaExceeded) {
            return Optional.empty();
        }

        try {
            // 1) 키워드 검색해서 contentId 얻기
            TourApiSearchResult searchResult = searchKeyword(keyword);
            if (searchResult == null) {
                log.info("TourAPI 검색 결과 없음 keyword={}", keyword);
                return Optional.empty();
            }

            // 2) 상세 정보(overview 등) 가져오기
            return fetchDetailCommon(searchResult);

        } catch (HttpClientErrorException.TooManyRequests e) {
            // 여기서 한도 초과 처리
            quotaExceeded = true;
            // 스택트레이스까지 찍으면 로그가 너무 시끄러워서 e는 안 넣고 메시지만 남김
            log.warn("TourAPI 호출 한도 초과! keyword={} 이후 요청은 모두 스킵합니다.", keyword);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("TourAPI 호출 실패 keyword={}", keyword, e);
            return Optional.empty();
        }
    }

    /**
     * searchKeyword2 호출 -> contentId, title, 주소, 이미지, 위경도…
     * (축제/공연/행사: contentTypeId = 15 고정)
     */
    private TourApiSearchResult searchKeyword(String keyword) throws Exception {

        // 축제/행사/공연 = 15
        String contentTypeId = "15";

        String url = baseUrl + "/searchKeyword2"
                + "?serviceKey=" + serviceKey                      // 서비스키 (이미 디코딩된 값 사용)
                + "&MobileOS=ETC"
                + "&MobileApp=" + URLEncoder.encode(appName, StandardCharsets.UTF_8)
                + "&numOfRows=1"
                + "&pageNo=1"
                + "&listYN=Y"
                + "&arrange=A"
                + "&contentTypeId=" + contentTypeId
                + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&_type=json";

        log.info("TourAPI searchKeyword2 URL = {}", url);

        String json = restTemplate.getForObject(url, String.class);
        if (json == null) {
            log.warn("TourAPI 응답이 null (keyword={})", keyword);
            return null;
        }

        JsonNode root = objectMapper.readTree(json);

        // 결과 코드 먼저 체크
        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asText();
        String resultMsg = header.path("resultMsg").asText();

        if (!"0000".equals(resultCode)) {
            log.warn("TourAPI 오류 resultCode={}, resultMsg={}, keyword={}",
                    resultCode, resultMsg, keyword);
            return null;
        }

        JsonNode items = root.path("response").path("body").path("items").path("item");

        // items 가 없거나 비어있으면 검색 결과 없음
        if (items.isMissingNode() || !items.isArray() || items.isEmpty()) {
            return null;
        }

        JsonNode item = items.get(0);

        TourApiSearchResult result = new TourApiSearchResult();
        result.setContentId(item.path("contentid").asText(null));
        // 혹시 응답에 contenttypeid 가 없으면 우리가 넣은 15로 보정
        String ctId = item.path("contenttypeid").asText(null);
        result.setContentTypeId(ctId != null ? ctId : contentTypeId);
        result.setTitle(item.path("title").asText(null));
        result.setAddr1(item.path("addr1").asText(null));
        result.setFirstImageUrl(item.path("firstimage").asText(null));
        result.setMapX(item.path("mapx").asText(null));
        result.setMapY(item.path("mapy").asText(null));

        if (result.getContentId() == null) {
            return null;
        }
        return result;
    }

    /**
     * detailCommon2 호출 -> overview(상세 설명) 보강
     */
    private Optional<TourApiFestivalInfo> fetchDetailCommon(TourApiSearchResult base) throws Exception {

        String url = baseUrl + "/detailCommon2"
                + "?serviceKey=" + serviceKey
                + "&MobileOS=ETC"
                + "&MobileApp=" + URLEncoder.encode(appName, StandardCharsets.UTF_8)
                + "&contentId=" + base.getContentId()
                + "&contentTypeId=" + base.getContentTypeId()
                + "&overviewYN=Y"
                + "&defaultYN=Y"
                + "&_type=json";

        log.info("TourAPI detailCommon2 URL = {}", url);

        String json = restTemplate.getForObject(url, String.class);
        String overview = null;

        if (json != null) {
            JsonNode root = objectMapper.readTree(json);

            // header 체크 (에러 나면 로그만 남기고 overview 없이 진행)
            JsonNode header = root.path("response").path("header");
            String resultCode = header.path("resultCode").asText();
            String resultMsg = header.path("resultMsg").asText();

            if (!"0000".equals(resultCode)) {
                log.warn("TourAPI detailCommon 오류 resultCode={}, resultMsg={}, contentId={}",
                        resultCode, resultMsg, base.getContentId());
            } else {
                JsonNode items = root.path("response").path("body").path("items").path("item");
                if (items.isArray() && !items.isEmpty()) {
                    overview = items.get(0).path("overview").asText(null);
                }
            }
        }

        TourApiFestivalInfo info = new TourApiFestivalInfo(
                base.getTitle(),
                overview,
                base.getFirstImageUrl(),
                base.getAddr1(),
                base.getMapX(),
                base.getMapY()
        );
        return Optional.of(info);
    }

    // searchKeyword 응답 중에서 우리가 쓸 정보만 담는 내부 DTO
    @lombok.Data
    private static class TourApiSearchResult {
        private String contentId;
        private String contentTypeId;
        private String title;
        private String addr1;
        private String firstImageUrl;
        private String mapX;
        private String mapY;
    }
}
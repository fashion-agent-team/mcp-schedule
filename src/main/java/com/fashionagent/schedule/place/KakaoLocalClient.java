package com.fashionagent.schedule.place;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 로컬 API 키워드 검색으로 장소 종류를 찾습니다.
 * https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword
 *
 * <p>키(KAKAO_REST_API_KEY)가 없으면 부르지 않고 빈 값을 돌려줍니다. 그때는 제목 키워드로만 자리를 정합니다.
 */
@Component
public class KakaoLocalClient implements PlaceLookup {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);

    private final RestClient restClient;
    private final boolean enabled;

    public KakaoLocalClient(
            @Value("${schedule.kakao.rest-api-key:}") String apiKey,
            @Value("${schedule.kakao.base-url:https://dapi.kakao.com}") String baseUrl) {
        this.enabled = apiKey != null && !apiKey.isBlank();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "KakaoAK " + (enabled ? apiKey : ""))
                .build();
        if (!enabled) {
            log.info("KAKAO_REST_API_KEY가 없어 장소 종류 찾기를 끕니다. 제목 키워드로만 자리를 정합니다.");
        }
    }

    @Override
    public Optional<PlaceInfo> find(String placeName) {
        if (!enabled || placeName == null || placeName.isBlank()) {
            return Optional.empty();
        }
        try {
            KeywordResponse response = restClient.get()
                    .uri(uri -> uri.path("/v2/local/search/keyword.json")
                            .queryParam("query", placeName)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(KeywordResponse.class);
            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                return Optional.empty();
            }
            Document doc = response.documents().getFirst();
            return Optional.of(PlaceInfo.of(doc.placeName(), doc.categoryName()));
        } catch (RuntimeException e) {
            // 지도 API가 실패해도 일정 등록은 계속합니다(제목 키워드로 정함).
            log.warn("카카오 로컬 검색 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeywordResponse(List<Document> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName) {
    }
}

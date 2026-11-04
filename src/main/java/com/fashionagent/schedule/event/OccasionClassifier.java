package com.fashionagent.schedule.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * 일정 제목과 장소 종류로 자리 종류(occasion)를 정합니다.
 *
 * <p>1) 제목 키워드(앱의 schedule_rules.dart 표를 그대로 옮김)가 맞으면 그걸 씁니다.
 * "친구 결혼식"은 장소가 호텔이어도 결혼식이기 때문입니다.
 * 2) 제목으로 못 정하면 카카오 로컬의 장소 분류(category_name)로 정합니다.
 * 3) 둘 다 아니면 casual 입니다.
 */
@Component
public class OccasionClassifier {

    private static final Map<Occasion, List<String>> TITLE_KEYWORDS = new LinkedHashMap<>();
    private static final Map<Occasion, List<String>> PLACE_KEYWORDS = new LinkedHashMap<>();

    static {
        TITLE_KEYWORDS.put(Occasion.mourning, List.of("장례", "조문", "빈소", "추도"));
        TITLE_KEYWORDS.put(Occasion.wedding, List.of("결혼식", "웨딩", "예식"));
        TITLE_KEYWORDS.put(Occasion.formal,
                List.of("면접", "발표", "상견례", "시상", "졸업식", "입학식", "세미나"));
        TITLE_KEYWORDS.put(Occasion.business,
                List.of("회의", "미팅", "출근", "회사", "거래처", "수업", "강의"));
        TITLE_KEYWORDS.put(Occasion.active, List.of("운동", "헬스", "등산", "산책", "러닝", "조깅",
                "요가", "필라테스", "축구", "농구"));

        // 카카오 로컬 category_name 예: "서비스,산업 > 장례식장", "가정,생활 > 예식장",
        // "여행 > 관광,명소 > 산", "스포츠,레저 > 테니스장"
        PLACE_KEYWORDS.put(Occasion.mourning, List.of("장례식장", "납골당", "추모공원", "공원묘지"));
        PLACE_KEYWORDS.put(Occasion.wedding, List.of("예식장", "웨딩홀", "웨딩컨벤션"));
        PLACE_KEYWORDS.put(Occasion.business, List.of("회사", "사무실", "오피스", "공공기관", "관공서",
                "학교", "대학교", "학원"));
        PLACE_KEYWORDS.put(Occasion.active, List.of("스포츠,레저", "산", "등산로", "체육관", "헬스",
                "피트니스", "운동장", "수영장", "골프"));
    }

    public Occasion fromTitle(String title) {
        return match(TITLE_KEYWORDS, title).orElse(Occasion.casual);
    }

    /** 카카오 category_name 전체("가정,생활 > 예식장")를 받아 자리 종류를 고릅니다. */
    public Optional<Occasion> fromPlaceCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }
        // "산"처럼 짧은 낱말이 "부동산" 등에 잘못 걸리지 않도록 " > " 로 나눈 마디 단위로 비교합니다.
        List<String> segments = List.of(categoryName.split("\\s*>\\s*"));
        for (Map.Entry<Occasion, List<String>> entry : PLACE_KEYWORDS.entrySet()) {
            for (String word : entry.getValue()) {
                boolean hit = word.length() <= 1
                        ? segments.stream().anyMatch(s -> s.equals(word))
                        : segments.stream().anyMatch(s -> s.contains(word));
                if (hit) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    public Occasion classify(String title, String placeCategoryName) {
        Occasion byTitle = fromTitle(title);
        if (byTitle != Occasion.casual) {
            return byTitle;
        }
        return fromPlaceCategory(placeCategoryName).orElse(Occasion.casual);
    }

    private static Optional<Occasion> match(Map<Occasion, List<String>> table, String text) {
        if (text == null) {
            return Optional.empty();
        }
        for (Map.Entry<Occasion, List<String>> entry : table.entrySet()) {
            if (entry.getValue().stream().anyMatch(text::contains)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }
}

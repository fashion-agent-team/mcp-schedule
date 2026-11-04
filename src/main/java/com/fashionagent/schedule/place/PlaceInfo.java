package com.fashionagent.schedule.place;

/**
 * 카카오 로컬에서 찾은 장소.
 *
 * @param name         카카오에 등록된 장소 이름 (예: "더채플앳청담")
 * @param categoryName 분류 전체 (예: "가정,생활 > 예식장")
 * @param category     분류의 마지막 마디 (예: "예식장"). 도구 출력의 placeCategory 입니다.
 */
public record PlaceInfo(String name, String categoryName, String category) {

    public static PlaceInfo of(String name, String categoryName) {
        String last = categoryName;
        if (categoryName != null && categoryName.contains(">")) {
            String[] parts = categoryName.split("\\s*>\\s*");
            last = parts[parts.length - 1];
        }
        return new PlaceInfo(name, categoryName, last == null ? null : last.trim());
    }
}

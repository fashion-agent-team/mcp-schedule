package com.fashionagent.schedule.event;

/** 자리 종류(occasion)를 무엇으로 정했는지. events.occasion_source */
public enum OccasionSource {
    /** 일정 제목 키워드 */
    title,
    /** 카카오 장소 분류 */
    place,
    /** 둘 다 못 정해서 casual */
    // "default"는 자바 예약어라 이름을 바꾸고 DB에는 @JsonValue 없이 컨버터로 저장합니다.
    fallback;

    public String dbValue() {
        return this == fallback ? "default" : name();
    }

    public static OccasionSource fromDb(String v) {
        return "default".equals(v) ? fallback : valueOf(v);
    }
}

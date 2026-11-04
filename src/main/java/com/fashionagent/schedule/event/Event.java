package com.fashionagent.schedule.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** schedule 스키마의 events 테이블. 테이블은 Flyway(V1__init.sql)가 만들고, 설계는 프로젝트 DB 문서(db/schema.sql)를 따릅니다. */
@Entity
@Table(name = "events", indexes = @Index(name = "idx_events_user_start", columnList = "user_id, start_at"))
public class Event {

    /** "e_" + 짧은 임의 문자열. 도구 입출력의 eventId 입니다. */
    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(length = 200)
    private String place;

    /** 카카오 로컬 검색 결과 id. 못 찾으면 null */
    @Column(name = "kakao_place_id", length = 20)
    private String kakaoPlaceId;

    /** 카카오 로컬에서 찾은 장소 분류의 마지막 마디. 예: "예식장" */
    @Column(name = "place_category", length = 100)
    private String placeCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Occasion occasion;

    /** occasion을 제목으로 정했는지(title), 장소로 정했는지(place), 못 정했는지(default) */
    @Convert(converter = OccasionSourceConverter.class)
    @Column(name = "occasion_source", nullable = false, length = 10)
    private OccasionSource occasionSource;

    /** 당일 알림을 보낸 시각. 아직이면 null. */
    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
    }

    public Event(String id, UUID userId, String title, Instant startAt, String place,
            String kakaoPlaceId, String placeCategory, Occasion occasion, OccasionSource occasionSource) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.startAt = startAt;
        this.place = place;
        this.kakaoPlaceId = kakaoPlaceId;
        this.placeCategory = placeCategory;
        this.occasion = occasion;
        this.occasionSource = occasionSource;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markNotified(Instant at) {
        this.notifiedAt = at;
    }

    public String getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTitle() { return title; }
    public Instant getStartAt() { return startAt; }
    public String getPlace() { return place; }
    public String getKakaoPlaceId() { return kakaoPlaceId; }
    public String getPlaceCategory() { return placeCategory; }
    public OccasionSource getOccasionSource() { return occasionSource; }
    public Occasion getOccasion() { return occasion; }
    public Instant getNotifiedAt() { return notifiedAt; }
}

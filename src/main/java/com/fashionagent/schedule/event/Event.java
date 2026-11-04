package com.fashionagent.schedule.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** schedule 스키마의 events 테이블(약속 문서 7절). */
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

    /** 카카오 로컬에서 찾은 장소 분류의 마지막 마디. 예: "예식장" */
    @Column(name = "place_category", length = 100)
    private String placeCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Occasion occasion;

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
            String placeCategory, Occasion occasion) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.startAt = startAt;
        this.place = place;
        this.placeCategory = placeCategory;
        this.occasion = occasion;
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
    public String getPlaceCategory() { return placeCategory; }
    public Occasion getOccasion() { return occasion; }
    public Instant getNotifiedAt() { return notifiedAt; }
}

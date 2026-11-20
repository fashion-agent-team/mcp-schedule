package com.fashionagent.schedule.place;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** schedule.places: 카카오 로컬에서 찾은 장소(V4). 이름·분류는 장소 id에 딸린 값이라 일정마다 다시 적지 않습니다. */
@Entity
@Table(name = "places")
public class Place {

    @Id
    @Column(name = "kakao_place_id", length = 20)
    private String id;

    @Column(name = "place_name", nullable = false, length = 200)
    private String name;

    /** 분류의 마지막 마디. 예: "예식장" */
    @Column(length = 100)
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Place() {
    }

    public Place(String id, String name, String category) {
        this.id = id;
        this.name = name;
        this.category = category;
    }

    /** 카카오 쪽 이름·분류가 바뀌었으면 맞춥니다. */
    public void refresh(String name, String category) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (category != null) {
            this.category = category;
        }
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

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
}

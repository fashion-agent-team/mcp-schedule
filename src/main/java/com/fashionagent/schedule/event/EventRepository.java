package com.fashionagent.schedule.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, String> {

    /** [from, to) 사이에 시작하는 일정을 시작 시각 순으로. */
    List<Event> findByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            UUID userId, Instant from, Instant to);

    Optional<Event> findByIdAndUserId(String id, UUID userId);

    /** 알림 대상: [from, to) 사이에 시작하고 아직 알리지 않은 모든 사용자의 일정. */
    List<Event> findByStartAtGreaterThanEqualAndStartAtLessThanAndNotifiedAtIsNull(Instant from, Instant to);
}

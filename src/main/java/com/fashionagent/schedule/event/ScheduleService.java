package com.fashionagent.schedule.event;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionagent.schedule.common.KoreanText;
import com.fashionagent.schedule.common.ScheduleException;
import com.fashionagent.schedule.place.PlaceInfo;
import com.fashionagent.schedule.place.PlaceLookup;

/** 일정 추가·조회·삭제. MCP 도구와 REST가 함께 씁니다. */
@Service
public class ScheduleService {

    private static final char[] ID_CHARS = "abcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EventRepository repository;
    private final PlaceLookup placeLookup;
    private final OccasionClassifier classifier;
    private final ZoneId zone;

    public ScheduleService(EventRepository repository, PlaceLookup placeLookup,
            OccasionClassifier classifier, @Value("${schedule.zone:Asia/Seoul}") String zone) {
        this.repository = repository;
        this.placeLookup = placeLookup;
        this.classifier = classifier;
        this.zone = ZoneId.of(zone);
    }

    public record AddResult(String eventId, Occasion occasion, String placeCategory, String spokenSummary) {
    }

    @Transactional
    public AddResult add(String userId, String title, String start, String place) {
        UUID user = parseUserId(userId);
        if (title == null || title.isBlank()) {
            throw ScheduleException.badRequest("title이 비어 있어요.");
        }
        String cleanTitle = title.strip();
        String cleanPlace = place == null || place.isBlank() ? null : place.strip();
        ZonedDateTime startAt = parseStart(start);

        PlaceInfo info = cleanPlace == null ? null : placeLookup.find(cleanPlace).orElse(null);
        String placeCategory = info == null ? null : info.category();
        OccasionClassifier.Result occasion = classifier.classify(cleanTitle, info == null ? null : info.categoryName());

        Event event = new Event(newId(), user, cleanTitle, startAt.toInstant(), cleanPlace,
                info == null ? null : info.id(), placeCategory, occasion.occasion(), occasion.source());
        repository.save(event);

        return new AddResult(event.getId(), occasion.occasion(), placeCategory, summary(startAt, cleanPlace, cleanTitle));
    }

    @Transactional(readOnly = true)
    public List<EventView> list(String userId, String date) {
        UUID user = parseUserId(userId);
        LocalDate day = parseDate(date);
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return repository
                .findByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(user, from, to)
                .stream()
                .map(e -> EventView.of(e, zone))
                .toList();
    }

    @Transactional
    public boolean delete(String userId, String eventId) {
        UUID user = parseUserId(userId);
        if (eventId == null || eventId.isBlank()) {
            throw ScheduleException.badRequest("eventId가 비어 있어요.");
        }
        Event event = repository.findByIdAndUserId(eventId.strip(), user)
                .orElseThrow(() -> ScheduleException.notFound("그 일정을 찾지 못했어요."));
        repository.delete(event);
        return true;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    static String summary(ZonedDateTime start, String place, String title) {
        String when = KoreanText.spokenDateTime(start);
        String where = place == null ? "" : place + "에서 ";
        return when + ", " + where + KoreanText.eulReul(title) + " 등록했어요.";
    }

    private UUID parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw ScheduleException.badRequest("userId가 비어 있어요.");
        }
        try {
            return UUID.fromString(userId.strip());
        } catch (IllegalArgumentException e) {
            throw ScheduleException.badRequest("userId는 UUID여야 해요.");
        }
    }

    /** "2026-11-14T14:00:00+09:00". 시간대가 빠지면 서버 시간대(Asia/Seoul)로 봅니다. */
    private ZonedDateTime parseStart(String start) {
        if (start == null || start.isBlank()) {
            throw ScheduleException.badRequest("start가 비어 있어요.");
        }
        String s = start.strip();
        try {
            return OffsetDateTime.parse(s).atZoneSameInstant(zone);
        } catch (DateTimeParseException ignored) {
            // 시간대 없는 값 시도
        }
        try {
            return LocalDateTime.parse(s).atZone(zone);
        } catch (DateTimeParseException e) {
            throw ScheduleException.badRequest("start는 2026-11-14T14:00:00+09:00 모양이어야 해요.");
        }
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return today();
        }
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            throw ScheduleException.badRequest("date는 2026-11-14 모양이어야 해요.");
        }
    }

    private static String newId() {
        StringBuilder sb = new StringBuilder("e_");
        for (int i = 0; i < 12; i++) {
            sb.append(ID_CHARS[RANDOM.nextInt(ID_CHARS.length)]);
        }
        return sb.toString();
    }
}

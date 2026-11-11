package com.fashionagent.schedule.event;

import java.security.SecureRandom;
import java.time.Clock;
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
    private final Clock clock;

    public ScheduleService(EventRepository repository, PlaceLookup placeLookup,
            OccasionClassifier classifier, @Value("${schedule.zone:Asia/Seoul}") String zone, Clock clock) {
        this.repository = repository;
        this.placeLookup = placeLookup;
        this.classifier = classifier;
        this.zone = ZoneId.of(zone);
        this.clock = clock;
    }

    /**
     * @param careTip 격식 일정이면 드라이클리닝 안내 문장, 아니면 null. spokenSummary 뒤에도 붙어 있습니다.
     */
    public record AddResult(String eventId, Occasion occasion, String placeCategory, String spokenSummary,
            String careTip) {
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

        // 격식 자리면 드라이클리닝 안내. 이미 3일 안쪽이면 지금 말한 것으로 끝내고 3일 전 알림은 보내지 않습니다.
        LocalDate today = today();
        String careTip = DryCleaningAdvice.atRegistration(occasion.occasion(), today, startAt).orElse(null);
        if (DryCleaningAdvice.applies(occasion.occasion())
                && DryCleaningAdvice.daysUntil(today, startAt) <= DryCleaningAdvice.REMIND_DAYS_BEFORE) {
            event.markCareReminded(Instant.now(clock));
        }
        repository.save(event);

        String spoken = summary(startAt, cleanPlace, cleanTitle);
        if (careTip != null) {
            spoken = spoken + " " + careTip;
        }
        return new AddResult(event.getId(), occasion.occasion(), placeCategory, spoken, careTip);
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

    /**
     * 앱을 열었을 때 인사 뒤에 읽어 줄 일정 안내.
     *
     * @param spoken 그대로 읽어 줄 문장
     * @param today 오늘 일정(시작 순)
     * @param upcoming 내일부터 3일 뒤까지의 격식 일정(면접·결혼식·장례식 등, 시작 순)
     */
    public record Briefing(String spoken, List<EventView> today, List<EventView> upcoming) {
    }

    /**
     * 오늘 일정과 3일 안에 있는 중요한(격식) 일정을 한 번에 읽어 줄 문장으로 만듭니다. 읽기만 하고 알림 기록은 바꾸지 않습니다.
     * <p>예: "오늘은 오후 7시에 친구랑 저녁 일정이 있어요. 모레 11월 13일 금요일 오후 2시에 면접 일정이 있어요. 입을 옷은 세탁소에 맡기셨어요? …"
     */
    @Transactional(readOnly = true)
    public Briefing briefing(String userId) {
        UUID user = parseUserId(userId);
        LocalDate day = today();
        List<Event> todays = repository.findByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                user, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
        List<Event> soon = repository.findByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                user, day.plusDays(1).atStartOfDay(zone).toInstant(),
                day.plusDays(DryCleaningAdvice.REMIND_DAYS_BEFORE + 1L).atStartOfDay(zone).toInstant())
                .stream().filter(e -> DryCleaningAdvice.applies(e.getOccasion())).toList();

        StringBuilder spoken = new StringBuilder();
        if (todays.isEmpty()) {
            spoken.append("오늘은 일정이 없어요.");
        } else if (todays.size() == 1) {
            spoken.append("오늘은 ").append(oneLine(todays.getFirst())).append(" 일정이 있어요.");
        } else {
            spoken.append("오늘 일정은 ").append(todays.size()).append("개예요. ");
            spoken.append(String.join(", ", todays.stream().map(this::oneLine).toList())).append(".");
        }
        for (Event e : soon) {
            spoken.append(" ").append(DryCleaningAdvice.upcoming(e.getTitle(), day, e.getStartAt().atZone(zone)));
        }
        if (!soon.isEmpty()) {
            spoken.append(" ").append(DryCleaningAdvice.ASK);
        }
        return new Briefing(spoken.toString(),
                todays.stream().map(e -> EventView.of(e, zone)).toList(),
                soon.stream().map(e -> EventView.of(e, zone)).toList());
    }

    /** "오후 2시에 더채플앳청담에서 친구 결혼식" */
    private String oneLine(Event e) {
        String where = e.getPlace() == null ? "" : e.getPlace() + "에서 ";
        return KoreanText.spokenTime(e.getStartAt().atZone(zone)) + "에 " + where + e.getTitle();
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
        return LocalDate.now(clock.withZone(zone));
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

package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.fashionagent.schedule.event.Event;
import com.fashionagent.schedule.event.EventRepository;
import com.fashionagent.schedule.event.ScheduleService;
import com.fashionagent.schedule.notify.CareReminder;
import com.fashionagent.schedule.notify.Notifier;
import com.fashionagent.schedule.place.PlaceLookup;

/** 등록 안내와 3일 전 드라이클리닝 알림을 날짜를 옮겨 가며 확인합니다. */
@SpringBootTest(properties = { "schedule.reminder.cron=-", "schedule.care-reminder.cron=-" })
class CareReminderTest {

    record Sent(String eventId, String message) {
    }

    static final List<Sent> SENT = new ArrayList<>();

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        PlaceLookup noKakao() {
            return name -> Optional.empty();
        }

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        }

        @Bean
        @Primary
        Notifier recordingNotifier() {
            return new Notifier() {
                @Override
                public boolean notifyToday(Event event, String message) {
                    return true;
                }

                @Override
                public boolean notifyBefore(Event event, String message) {
                    SENT.add(new Sent(event.getId(), message));
                    return true;
                }
            };
        }
    }

    @Autowired
    ScheduleService service;
    @Autowired
    CareReminder reminder;
    @Autowired
    EventRepository repository;
    @Autowired
    Clock clock;

    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void reset() {
        SENT.clear();
    }

    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    /** 한국 시각 그날 아침 8시로 옮깁니다. */
    private void today(String date) {
        ((MutableClock) clock).set(Instant.parse(date + "T08:00:00+09:00"));
    }

    @Test
    void 열흘_전에_등록한_면접은_3일_전_아침에_한_번만_알린다() {
        today("2026-11-04");
        var added = service.add(userId, "면접", "2026-11-14T10:00:00+09:00", null);
        assertThat(added.careTip()).isEqualTo("그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        assertThat(added.spokenSummary())
                .isEqualTo("11월 14일 토요일 오전 10시, 면접을 등록했어요. 그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");

        today("2026-11-10");
        reminder.sendCareReminders();
        assertThat(SENT).isEmpty();

        today("2026-11-11");
        reminder.sendCareReminders();
        assertThat(SENT).extracting(Sent::eventId).containsExactly(added.eventId());
        assertThat(SENT.getFirst().message()).startsWith("3일 뒤 11월 14일 토요일 오전 10시에 면접 일정이 있어요.");

        today("2026-11-12");
        reminder.sendCareReminders();
        assertThat(SENT).hasSize(1);
        assertThat(repository.findById(added.eventId()).orElseThrow().getCareRemindedAt()).isNotNull();
    }

    @Test
    void 사흘_전에_서버가_꺼져_있었으면_다음_날_보낸다() {
        today("2026-11-01");
        var added = service.add(userId, "친구 결혼식", "2026-11-14T14:00:00+09:00", null);
        today("2026-11-12");
        reminder.sendCareReminders();
        assertThat(SENT).extracting(Sent::message).singleElement().asString().startsWith("모레 11월 14일 토요일");
        assertThat(SENT.getFirst().eventId()).isEqualTo(added.eventId());
    }

    @Test
    void 가까이_등록하면_등록할_때만_말하고_따로_알리지_않는다() {
        today("2026-11-12");
        var added = service.add(userId, "면접", "2026-11-14T10:00:00+09:00", null);
        assertThat(added.careTip()).startsWith("그날 입을 옷은 오늘이나 내일");
        reminder.sendCareReminders();
        assertThat(SENT).isEmpty();
    }

    @Test
    void 격식_아닌_일정은_말하지도_알리지도_않는다() {
        today("2026-11-04");
        var added = service.add(userId, "친구랑 저녁", "2026-11-14T19:00:00+09:00", null);
        assertThat(added.careTip()).isNull();
        assertThat(added.spokenSummary()).isEqualTo("11월 14일 토요일 오후 7시, 친구랑 저녁을 등록했어요.");
        today("2026-11-11");
        reminder.sendCareReminders();
        assertThat(SENT).isEmpty();
    }

    @Test
    void 당일_일정은_아침_알림이_맡는다() {
        today("2026-11-04");
        service.add(userId, "면접", "2026-11-14T10:00:00+09:00", null);
        today("2026-11-14");
        reminder.sendCareReminders();
        assertThat(SENT).isEmpty();
    }

    @Test
    void 브리핑_일정이_없으면() {
        today("2026-11-11");
        assertThat(service.briefing(userId).spoken()).isEqualTo("오늘은 일정이 없어요.");
    }

    @Test
    void 브리핑_오늘_일정_여러_개와_3일_안_중요한_일정() {
        today("2026-11-11");
        service.add(userId, "팀 회의", "2026-11-11T10:00:00+09:00", null);
        service.add(userId, "친구 결혼식", "2026-11-11T14:00:00+09:00", "더채플앳청담");
        service.add(userId, "친구랑 저녁", "2026-11-12T19:00:00+09:00", null); // 격식 아님 → 안 읽음
        service.add(userId, "면접", "2026-11-14T10:00:00+09:00", null);
        service.add(userId, "발표", "2026-11-15T10:00:00+09:00", null); // 4일 뒤 → 안 읽음
        var b = service.briefing(userId);
        assertThat(b.spoken()).isEqualTo("오늘 일정은 2개예요. 오전 10시에 팀 회의, 오후 2시에 더채플앳청담에서 친구 결혼식. "
                + "3일 뒤 11월 14일 토요일 오전 10시에 면접 일정이 있어요. "
                + "입을 옷은 세탁소에 맡기셨어요? 드라이클리닝은 보통 1~3일 걸려서 아직이면 오늘 맡기면 좋아요.");
        assertThat(b.today()).hasSize(2);
        assertThat(b.upcoming()).extracting(e -> e.title()).containsExactly("면접");
    }

    @Test
    void 브리핑은_다른_사람_일정을_읽지_않는다() {
        today("2026-11-11");
        service.add(UUID.randomUUID().toString(), "면접", "2026-11-12T10:00:00+09:00", null);
        assertThat(service.briefing(userId).spoken()).isEqualTo("오늘은 일정이 없어요.");
    }
}

package com.fashionagent.schedule.notify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fashionagent.schedule.event.DryCleaningAdvice;
import com.fashionagent.schedule.event.Event;
import com.fashionagent.schedule.event.EventRepository;
import com.fashionagent.schedule.event.ScheduleService;

/**
 * 격식 일정 3일 전 아침에 "입을 옷 세탁소에 맡기셨어요?"를 알립니다. 도구가 아니라 서버 스케줄러입니다.
 *
 * <p>대상은 내일부터 3일 뒤까지 시작하는 격식 일정(장례식·결혼식·면접 등) 중 아직 안내하지 않은 것입니다.
 * 3일 전에 서버가 꺼져 있었으면 다음 날(2일 전) 아침에 보냅니다. 오늘 일정은 아침 알림(MorningReminder)이 맡습니다.
 */
@Component
public class CareReminder {

    private static final Logger log = LoggerFactory.getLogger(CareReminder.class);

    private final EventRepository repository;
    private final ScheduleService service;
    private final Notifier notifier;
    private final Clock clock;

    public CareReminder(EventRepository repository, ScheduleService service, Notifier notifier, Clock clock) {
        this.repository = repository;
        this.service = service;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(cron = "${schedule.care-reminder.cron:0 0 8 * * *}", zone = "${schedule.zone:Asia/Seoul}")
    @Transactional
    public void sendCareReminders() {
        LocalDate today = service.today();
        Instant from = today.plusDays(1).atStartOfDay(service.zone()).toInstant();
        Instant to = today.plusDays(DryCleaningAdvice.REMIND_DAYS_BEFORE + 1L).atStartOfDay(service.zone()).toInstant();
        List<Event> events = repository.findByOccasionInAndStartAtGreaterThanEqualAndStartAtLessThanAndCareRemindedAtIsNull(
                DryCleaningAdvice.OCCASIONS, from, to);
        int sent = 0;
        for (Event e : events) {
            String message = DryCleaningAdvice.reminder(e.getTitle(), today, e.getStartAt().atZone(service.zone()));
            if (notifier.notifyBefore(e, message)) {
                e.markCareReminded(Instant.now(clock));
                sent++;
            }
        }
        log.info("드라이클리닝 알림 {}건 / 대상 {}건", sent, events.size());
    }
}

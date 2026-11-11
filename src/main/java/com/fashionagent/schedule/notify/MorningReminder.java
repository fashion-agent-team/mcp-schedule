package com.fashionagent.schedule.notify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fashionagent.schedule.common.KoreanText;
import com.fashionagent.schedule.event.Event;
import com.fashionagent.schedule.event.EventRepository;
import com.fashionagent.schedule.event.ScheduleService;

/** 일정 당일 아침 8시(Asia/Seoul)에 그날 일정을 알립니다. 도구가 아니라 서버 스케줄러입니다. */
@Component
public class MorningReminder {

    private static final Logger log = LoggerFactory.getLogger(MorningReminder.class);

    private final EventRepository repository;
    private final ScheduleService service;
    private final Notifier notifier;

    public MorningReminder(EventRepository repository, ScheduleService service, Notifier notifier) {
        this.repository = repository;
        this.service = service;
        this.notifier = notifier;
    }

    @Scheduled(cron = "${schedule.reminder.cron:0 0 8 * * *}", zone = "${schedule.zone:Asia/Seoul}")
    @Transactional
    public void sendTodayReminders() {
        LocalDate today = service.today();
        Instant from = today.atStartOfDay(service.zone()).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(service.zone()).toInstant();
        List<Event> events = repository.findByStartAtGreaterThanEqualAndStartAtLessThanAndNotifiedAtIsNull(from, to);
        int sent = 0;
        for (Event e : events) {
            if (!e.isRemind()) {
                continue; // 사용자가 "알림은 됐어"라고 한 일정
            }
            if (notifier.notifyToday(e, message(e))) {
                e.markNotified(Instant.now());
                sent++;
            }
        }
        log.info("당일 알림 {}건 / 대상 {}건", sent, events.size());
    }

    String message(Event e) {
        String time = KoreanText.spokenTime(e.getStartAt().atZone(service.zone()));
        String where = e.getPlace() == null ? "" : e.getPlace() + "에서 ";
        return "오늘 " + time + "에 " + where + e.getTitle() + " 일정이 있어요. 옷 추천을 받아 보세요.";
    }
}

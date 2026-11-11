package com.fashionagent.schedule.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fashionagent.schedule.event.Event;

/** FCM을 붙이기 전까지 쓰는 알림. 로그에 사용자 id와 일정 id만 남깁니다. */
@Component
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public boolean notifyToday(Event event, String message) {
        log.info("[알림 자리] user={} event={} : {}", event.getUserId(), event.getId(), message);
        return true;
    }

    @Override
    public boolean notifyBefore(Event event, String message) {
        log.info("[미리 알림 자리] user={} event={} : {}", event.getUserId(), event.getId(), message);
        return true;
    }
}

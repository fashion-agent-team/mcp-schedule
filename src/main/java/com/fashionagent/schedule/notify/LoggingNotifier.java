package com.fashionagent.schedule.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fashionagent.schedule.event.Event;

/** FCM을 붙이기 전까지 쓰는 알림. 로그에는 일정 id만 남깁니다(사용자 id·일정 이름 같은 알림 문장은 남기지 않음). */
@Component
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public boolean notifyToday(Event event, String message) {
        log.info("[알림 자리] event={} ({}자)", event.getId(), message == null ? 0 : message.length());
        return true;
    }

    @Override
    public boolean notifyBefore(Event event, String message) {
        log.info("[미리 알림 자리] event={} ({}자)", event.getId(), message == null ? 0 : message.length());
        return true;
    }
}

package com.fashionagent.schedule.notify;

import com.fashionagent.schedule.event.Event;

/**
 * 일정 당일 알림을 보내는 곳. 지금은 로그만 남기는 LoggingNotifier 하나입니다.
 *
 * <p>TODO FCM: 푸시 토큰은 gateway의 account.push_tokens에 있어 이 서비스가 직접 읽을 수 없습니다(약속 문서 7절).
 * 토큰을 받는 방법(gateway 내부 API 등)을 정한 뒤 FcmNotifier를 만들어 바꿔 끼웁니다.
 */
public interface Notifier {

    /** 보냈으면 true. false면 다음 번에 다시 시도합니다. */
    boolean notifyToday(Event event, String message);
}

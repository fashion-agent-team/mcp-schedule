package com.fashionagent.schedule.notify;

import com.fashionagent.schedule.event.Event;

/**
 * 일정 알림을 보내는 곳. 지금은 로그만 남기는 LoggingNotifier 하나입니다.
 *
 * <p>TODO 푸시: 푸시 토큰은 gateway의 account.push_tokens에 있어 이 서비스가 직접 읽을 수 없습니다(약속 문서 7절).
 * gateway에 발송 API가 생기면 그걸 부르는 Notifier(예: GatewayPushNotifier)를 만들어 바꿔 끼웁니다.
 */
public interface Notifier {

    /** 일정 당일 아침 알림. 보냈으면 true. false면 다음 번에 다시 시도합니다. */
    boolean notifyToday(Event event, String message);

    /** 일정 며칠 전 알림(격식 일정 드라이클리닝 안내). 보냈으면 true. false면 다음 날 다시 시도합니다. */
    boolean notifyBefore(Event event, String message);
}

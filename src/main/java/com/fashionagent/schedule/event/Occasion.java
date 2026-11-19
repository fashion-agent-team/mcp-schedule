package com.fashionagent.schedule.event;

/**
 * 일정의 자리 종류. 앱 레포 lib/styling/schedule_rules.dart 의 Occasion 과 같습니다.
 * 순서가 격식 순서입니다(앞일수록 엄격한 자리).
 */
public enum Occasion {
    /** 장례식, 조문 */
    mourning,
    /** 결혼식 하객 */
    wedding,
    /** 면접, 발표, 상견례처럼 격식 있는 자리 */
    formal,
    /** 회의, 출근, 수업 */
    business,
    /** 운동, 등산 */
    active,
    /** 그 밖의 일정 */
    casual
}

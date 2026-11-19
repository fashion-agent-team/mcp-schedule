package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.fashionagent.schedule.common.KoreanText;

class KoreanTextTest {

    @Test
    void 을를() {
        assertThat(KoreanText.eulReul("결혼식")).isEqualTo("결혼식을");
        assertThat(KoreanText.eulReul("회의")).isEqualTo("회의를");
        assertThat(KoreanText.eulReul("PT")).isEqualTo("PT을(를)");
    }

    @Test
    void 날짜와_시각() {
        ZonedDateTime t = ZonedDateTime.of(2026, 11, 14, 14, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        assertThat(KoreanText.spokenDateTime(t)).isEqualTo("11월 14일 토요일 오후 2시");
        assertThat(KoreanText.spokenTime(t.withHour(0).withMinute(30))).isEqualTo("오전 12시 30분");
    }
}

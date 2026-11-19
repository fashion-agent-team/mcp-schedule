package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.fashionagent.schedule.event.DryCleaningAdvice;
import com.fashionagent.schedule.event.Occasion;

class DryCleaningAdviceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final ZonedDateTime NOV14_2PM = ZonedDateTime.parse("2026-11-14T14:00:00+09:00[Asia/Seoul]");

    @Test
    void 격식_자리만_안내한다() {
        assertThat(DryCleaningAdvice.applies(Occasion.formal)).isTrue();
        assertThat(DryCleaningAdvice.applies(Occasion.wedding)).isTrue();
        assertThat(DryCleaningAdvice.applies(Occasion.mourning)).isTrue();
        assertThat(DryCleaningAdvice.applies(Occasion.business)).isFalse();
        assertThat(DryCleaningAdvice.applies(Occasion.casual)).isFalse();
        assertThat(DryCleaningAdvice.applies(Occasion.active)).isFalse();
        assertThat(DryCleaningAdvice.atRegistration(Occasion.casual, TODAY, NOV14_2PM)).isEmpty();
    }

    @Test
    void 등록할_때_남은_날에_맞춰_말한다() {
        assertThat(DryCleaningAdvice.atRegistration(Occasion.formal, TODAY, NOV14_2PM.plusDays(7)))
                .hasValue("그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        assertThat(DryCleaningAdvice.atRegistration(Occasion.formal, TODAY, NOV14_2PM))
                .hasValue("그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        assertThat(DryCleaningAdvice.atRegistration(Occasion.formal, TODAY, NOV14_2PM.minusDays(1)))
                .hasValue("그날 입을 옷은 오늘이나 내일 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        assertThat(DryCleaningAdvice.atRegistration(Occasion.mourning, TODAY, NOV14_2PM.minusDays(2)))
                .hasValue("내일이라 드라이클리닝은 빠듯해요. 빨리 해 주는 세탁소를 찾아보거나, 입을 옷을 오늘 미리 꺼내 걸어 두세요.");
        // 오늘·지난 일정은 말하지 않음
        assertThat(DryCleaningAdvice.atRegistration(Occasion.formal, TODAY, NOV14_2PM.minusDays(3))).isEmpty();
        assertThat(DryCleaningAdvice.atRegistration(Occasion.formal, TODAY, NOV14_2PM.minusDays(10))).isEmpty();
    }

    @Test
    void 미리_알림_문장() {
        assertThat(DryCleaningAdvice.reminder("면접", TODAY, NOV14_2PM))
                .isEqualTo("3일 뒤 11월 14일 토요일 오후 2시에 면접 일정이 있어요. "
                        + "입을 옷은 세탁소에 맡기셨어요? 드라이클리닝은 보통 1~3일 걸려서 아직이면 오늘 맡기면 좋아요.");
        assertThat(DryCleaningAdvice.reminder("면접", TODAY, NOV14_2PM.minusDays(1))).startsWith("모레 11월 13일 금요일");
        assertThat(DryCleaningAdvice.reminder("면접", TODAY, NOV14_2PM.minusDays(2))).startsWith("내일 11월 12일 목요일");
    }
}

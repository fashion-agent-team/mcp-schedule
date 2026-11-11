package com.fashionagent.schedule.event;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.fashionagent.schedule.common.KoreanText;

/**
 * 격식 자리(면접·발표·결혼식·장례식 등) 옷의 드라이클리닝 안내 문장.
 *
 * <p>시각장애인은 혼자 다림질하기 어렵고, 사진으로 구김을 찾기도 어렵습니다. 그래서 구김을 찾는 대신
 * 격식 일정이 잡히면 그날 입을 옷을 미리 세탁소에 맡기라고 말해 줍니다. 드라이클리닝은 보통 1~3일 걸려서
 * 당일 아침 알림으로는 늦습니다. 그래서 두 번 말합니다.
 * <ol>
 * <li>일정을 등록할 때: schedule_add의 spokenSummary 뒤에 붙입니다.</li>
 * <li>일정 3일 전 아침: {@link #REMIND_DAYS_BEFORE}일 전에 알림을 보냅니다(notify/CareReminder).</li>
 * </ol>
 * 등록할 때 이미 3일 안쪽이면 등록 안내로 끝내고 따로 알림은 보내지 않습니다.
 */
public final class DryCleaningAdvice {

    /** 안내할 자리 종류. 회의·출근(business)은 매일 입는 옷이라 뺍니다. */
    public static final Set<Occasion> OCCASIONS = EnumSet.of(Occasion.mourning, Occasion.wedding, Occasion.formal);

    /** 며칠 전에 알릴지. 드라이클리닝이 보통 1~3일 걸려서 3일 전입니다. */
    public static final int REMIND_DAYS_BEFORE = 3;

    private DryCleaningAdvice() {
    }

    public static boolean applies(Occasion occasion) {
        return occasion != null && OCCASIONS.contains(occasion);
    }

    /** 오늘부터 일정 날짜까지 남은 날 수(달력 기준). 오늘이면 0, 내일이면 1. */
    public static long daysUntil(LocalDate today, ZonedDateTime start) {
        return ChronoUnit.DAYS.between(today, start.toLocalDate());
    }

    /**
     * 등록할 때 덧붙일 안내. 격식 자리가 아니거나 오늘·지난 일정이면 비어 있습니다.
     * <ul>
     * <li>3일 이상 남음: 미리 맡기면 좋다</li>
     * <li>모레: 오늘이나 내일 맡기면 좋다</li>
     * <li>내일: 빠듯하니 빨리 되는 세탁소를 찾거나 미리 꺼내 걸어 두기</li>
     * </ul>
     */
    public static Optional<String> atRegistration(Occasion occasion, LocalDate today, ZonedDateTime start) {
        if (!applies(occasion)) {
            return Optional.empty();
        }
        long days = daysUntil(today, start);
        if (days >= REMIND_DAYS_BEFORE) {
            return Optional.of("그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        }
        if (days == 2) {
            return Optional.of("그날 입을 옷은 오늘이나 내일 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        }
        if (days == 1) {
            return Optional.of("내일이라 드라이클리닝은 빠듯해요. 빨리 해 주는 세탁소를 찾아보거나, 입을 옷을 오늘 미리 꺼내 걸어 두세요.");
        }
        return Optional.empty();
    }

    /** 미리 묻는 말. 며칠 전 알림과 앱 인사 브리핑 끝에 붙입니다. */
    public static final String ASK = "입을 옷은 세탁소에 맡기셨어요? 드라이클리닝은 보통 1~3일 걸려서 아직이면 오늘 맡기면 좋아요.";

    /** 며칠 뒤 일정 한 문장. 예: "3일 뒤 11월 14일 토요일 오후 2시에 면접 일정이 있어요." */
    public static String upcoming(String title, LocalDate today, ZonedDateTime start) {
        long days = daysUntil(today, start);
        String when = switch ((int) days) {
            case 1 -> "내일";
            case 2 -> "모레";
            default -> days + "일 뒤";
        };
        return when + " " + KoreanText.spokenDateTime(start) + "에 " + title + " 일정이 있어요.";
    }

    /** 며칠 전 알림 문장. 예: "3일 뒤 11월 14일 토요일 오후 2시에 면접 일정이 있어요. 입을 옷은 세탁소에 맡기셨어요? …" */
    public static String reminder(String title, LocalDate today, ZonedDateTime start) {
        return upcoming(title, today, start) + " " + ASK;
    }
}

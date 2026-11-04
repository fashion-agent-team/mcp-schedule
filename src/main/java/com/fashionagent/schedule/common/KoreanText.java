package com.fashionagent.schedule.common;

import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/** 읽어 줄 한국어 문장 도우미. */
public final class KoreanText {

    private KoreanText() {
    }

    /** 받침이 있으면 "을", 없으면 "를". 한글이 아니면 "을(를)". */
    public static String eulReul(String word) {
        return word + josa(word, "을", "를");
    }

    /** "11월 14일 토요일 오후 2시", "11월 4일 화요일 오전 10시 30분" */
    public static String spokenDateTime(ZonedDateTime t) {
        String day = t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        return t.getMonthValue() + "월 " + t.getDayOfMonth() + "일 " + day + " " + spokenTime(t);
    }

    public static String spokenTime(ZonedDateTime t) {
        String ampm = t.getHour() < 12 ? "오전" : "오후";
        int hour = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return ampm + " " + hour + "시" + (t.getMinute() == 0 ? "" : " " + t.getMinute() + "분");
    }

    static String josa(String word, String withFinal, String withoutFinal) {
        if (word == null || word.isEmpty()) {
            return withFinal + "(" + withoutFinal + ")";
        }
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return withFinal + "(" + withoutFinal + ")";
        }
        return (last - 0xAC00) % 28 == 0 ? withoutFinal : withFinal;
    }

}

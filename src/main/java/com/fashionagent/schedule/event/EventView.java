package com.fashionagent.schedule.event;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 도구·REST 출력의 일정 한 건(mcp-tools.md의 schedule_list 모양). */
public record EventView(
        String eventId,
        String title,
        String start,
        String place,
        String placeCategory,
        Occasion occasion) {

    /** 약속 문서 1절: 2026-11-20T14:00:00+09:00 (초까지, 시간대 포함) */
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public static EventView of(Event e, ZoneId zone) {
        return new EventView(
                e.getId(),
                e.getTitle(),
                START.format(e.getStartAt().atZone(zone)),
                e.getPlace(),
                e.getPlaceCategory(),
                e.getOccasion());
    }
}

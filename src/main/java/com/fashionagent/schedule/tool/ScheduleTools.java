package com.fashionagent.schedule.tool;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.fashionagent.schedule.common.ApiError;
import com.fashionagent.schedule.common.ScheduleException;
import com.fashionagent.schedule.event.EventView;
import com.fashionagent.schedule.event.ScheduleService;

/**
 * agent가 부르는 MCP 도구 3개(약속 문서 mcp-tools.md).
 *
 * <p>설명은 Gemini가 읽고 도구를 고르는 근거라 한국어로 언제 쓰는지까지 적습니다.
 * userId는 agent가 X-User-Id 값으로 덮어써서 넣습니다. 실패하면 예외 대신 {"error": {...}}를 돌려줍니다.
 */
@Component
public class ScheduleTools {

    private final ScheduleService service;

    public ScheduleTools(ScheduleService service) {
        this.service = service;
    }

    public record ListResult(List<EventView> events) {
    }

    public record DeleteResult(boolean deleted) {
    }

    @McpTool(name = "schedule_add",
            description = "사용자의 일정을 하나 등록합니다. \"다음 주 토요일 두 시에 친구 결혼식\"처럼 일정을 넣어 달라고 할 때 씁니다. "
                    + "말에서 제목·시작 시각·장소를 나눠 넣으세요. 장소가 있으면 지도에서 장소 종류를 찾아 자리 종류(occasion)를 정합니다. "
                    + "돌려주는 spokenSummary를 사용자에게 그대로 읽어 주면 됩니다.")
    public Object scheduleAdd(
            @McpToolParam(description = "사용자 id(UUID). agent가 채웁니다.", required = true) String userId,
            @McpToolParam(description = "일정 이름. 사용자가 말한 그대로, 예: \"친구 결혼식\", \"면접\"", required = true) String title,
            @McpToolParam(description = "시작 시각 ISO-8601, 시간대 포함. 예: 2026-11-14T14:00:00+09:00", required = true) String start,
            @McpToolParam(description = "장소 이름. 말하지 않았으면 비웁니다. 예: \"더채플앳청담\"", required = false) String place) {
        return run(() -> service.add(userId, title, start, place));
    }

    @McpTool(name = "schedule_list",
            description = "날짜별 일정을 장소 종류(placeCategory)와 자리 종류(occasion)와 함께 돌려줍니다. "
                    + "옷 추천 1단계에서 오늘 날짜로 부르고, \"오늘 일정 뭐야?\", \"토요일에 뭐 있어?\"에도 씁니다. "
                    + "일정이 없으면 events가 빈 배열입니다. 어떤 옷이 맞는지는 판단하지 않습니다.")
    public Object scheduleList(
            @McpToolParam(description = "사용자 id(UUID). agent가 채웁니다.", required = true) String userId,
            @McpToolParam(description = "날짜 YYYY-MM-DD (Asia/Seoul 기준). 비우면 오늘", required = false) String date) {
        return run(() -> new ListResult(service.list(userId, date)));
    }

    @McpTool(name = "schedule_delete",
            description = "일정을 하나 지웁니다. \"토요일 결혼식 지워 줘\"처럼 말하면 먼저 schedule_list로 eventId를 찾은 뒤 부르세요.")
    public Object scheduleDelete(
            @McpToolParam(description = "사용자 id(UUID). agent가 채웁니다.", required = true) String userId,
            @McpToolParam(description = "지울 일정의 eventId (schedule_list 결과의 eventId)", required = true) String eventId) {
        return run(() -> new DeleteResult(service.delete(userId, eventId)));
    }

    private static Object run(Supplier<Object> action) {
        try {
            return action.get();
        } catch (ScheduleException e) {
            return e.toApiError();
        } catch (RuntimeException e) {
            return ApiError.of("internal_error", "일정 처리 중 오류가 났어요.");
        }
    }
}

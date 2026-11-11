package com.fashionagent.schedule.tool;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.fashionagent.schedule.common.ApiError;
import com.fashionagent.schedule.common.ScheduleException;
import com.fashionagent.schedule.event.EventView;
import com.fashionagent.schedule.event.ScheduleService;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * agent가 부르는 MCP 도구 3개(약속 문서 mcp-tools.md).
 *
 * <p>방식 비교(Spring AI @McpTool / @Tool / MCP Java SDK) 결과 @McpTool을 씁니다. 스키마가 메서드에서 자동으로 나오고,
 * 도구 성격(annotations)과 isError를 직접 정할 수 있기 때문입니다.
 *
 * <p>설명은 Gemini가 읽고 도구를 고르는 근거라 한국어로 언제 쓰는지까지 적습니다.
 * userId는 agent가 X-User-Id 값으로 덮어써서 넣습니다.
 * 결과는 JSON 글(text)과 같은 값의 structuredContent로 돌려주고, 실패하면 {"error": {...}}에 isError=true를 붙입니다.
 */
@Component
public class ScheduleTools {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTools.class);

    private final ScheduleService service;
    private final JsonMapper json;

    public ScheduleTools(ScheduleService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    public record ListResult(List<EventView> events) {
    }

    public record DeleteResult(boolean deleted) {
    }

    @McpTool(name = "schedule_add",
            description = "사용자의 일정을 하나 등록합니다. \"다음 주 토요일 두 시에 친구 결혼식\"처럼 일정을 넣어 달라고 할 때 씁니다. "
                    + "말에서 제목·시작 시각·장소를 나눠 넣으세요. 장소가 있으면 지도에서 장소 종류를 찾아 자리 종류(occasion)를 정합니다. "
                    + "돌려주는 spokenSummary를 사용자에게 그대로 읽어 주면 됩니다. "
                    + "면접·결혼식·장례식 같은 격식 자리면 spokenSummary 끝에 드라이클리닝 안내(careTip)가 붙어 있습니다.",
            annotations = @McpAnnotations(title = "일정 등록", readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = true))
    public CallToolResult scheduleAdd(
            @McpToolParam(description = "사용자 id(UUID). agent가 채웁니다.", required = true) String userId,
            @McpToolParam(description = "일정 이름. 사용자가 말한 그대로, 예: \"친구 결혼식\", \"면접\"", required = true) String title,
            @McpToolParam(description = "시작 시각 ISO-8601, 시간대 포함. 예: 2026-11-14T14:00:00+09:00", required = true) String start,
            @McpToolParam(description = "장소 이름. 말하지 않았으면 비웁니다. 예: \"더채플앳청담\"", required = false) String place) {
        return run(() -> service.add(userId, title, start, place));
    }

    @McpTool(name = "schedule_list",
            description = "날짜별 일정을 장소 종류(placeCategory)와 자리 종류(occasion)와 함께 돌려줍니다. "
                    + "옷 추천 1단계에서 오늘 날짜로 부르고, \"오늘 일정 뭐야?\", \"토요일에 뭐 있어?\"에도 씁니다. "
                    + "일정이 없으면 events가 빈 배열입니다. 어떤 옷이 맞는지는 판단하지 않습니다.",
            annotations = @McpAnnotations(title = "일정 보기", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public CallToolResult scheduleList(
            @McpToolParam(description = "사용자 id(UUID). agent가 채웁니다.", required = true) String userId,
            @McpToolParam(description = "날짜 YYYY-MM-DD (Asia/Seoul 기준). 비우면 오늘", required = false) String date) {
        return run(() -> new ListResult(service.list(userId, date)));
    }

    @McpTool(name = "schedule_delete",
            description = "일정을 하나 지웁니다. \"토요일 결혼식 지워 줘\"처럼 말하면 먼저 schedule_list로 eventId를 찾은 뒤 부르세요.",
            annotations = @McpAnnotations(title = "일정 삭제", readOnlyHint = false, destructiveHint = true,
                    idempotentHint = true, openWorldHint = false))
    public CallToolResult scheduleDelete(
            @McpToolParam(description = "사용자 id(UUID). agent가 채웁니다.", required = true) String userId,
            @McpToolParam(description = "지울 일정의 eventId (schedule_list 결과의 eventId)", required = true) String eventId) {
        return run(() -> new DeleteResult(service.delete(userId, eventId)));
    }

    private CallToolResult run(Supplier<Object> action) {
        try {
            return result(action.get(), false);
        } catch (ScheduleException e) {
            return result(e.toApiError(), true);
        } catch (RuntimeException e) {
            log.error("도구 처리 중 오류", e);
            return result(ApiError.of("internal_error", "일정 처리 중 오류가 났어요."), true);
        }
    }

    private CallToolResult result(Object value, boolean isError) {
        return CallToolResult.builder()
                .addTextContent(json.writeValueAsString(value))
                .structuredContent(json.convertValue(value, Object.class))
                .isError(isError)
                .build();
    }
}

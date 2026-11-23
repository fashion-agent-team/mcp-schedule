package com.fashionagent.schedule.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fashionagent.schedule.common.ScheduleException;
import com.fashionagent.schedule.event.Occasion;
import com.fashionagent.schedule.event.ScheduleService;
import com.fashionagent.schedule.event.ScheduleService.AddResult;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 도구 계층 테스트. REST의 ScheduleControllerTest(@WebMvcTest)와 같은 자리입니다.
 * ScheduleService는 Mock으로 바꾸고(정상 동작한다고 가정), 도구가 값을 그대로 넘기는지와
 * 돌려주는 결과·isError·오류 코드만 봅니다. 진짜 DB로 끝까지 보는 건 ScheduleIntegrationTest입니다.
 *
 * <p>MCP에는 HTTP 상태 코드가 없어서 성공/실패는 isError로, 이유는 {"error": {"code"}}로 알립니다
 * (bad_request ≈ 400, not_found ≈ 404, internal_error ≈ 500).
 */
class ScheduleToolsTest {

    private static final String USER = "7f1e2a3b-0000-4000-8000-000000000001";

    private ScheduleService service;
    private ScheduleTools tools;

    @BeforeEach
    void setUp() {
        service = mock(ScheduleService.class);
        tools = new ScheduleTools(service, JsonMapper.builder().build());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(CallToolResult result) {
        return (Map<String, Object>) result.structuredContent();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> error(CallToolResult result) {
        return (Map<String, Object>) body(result).get("error");
    }

    @Test
    @DisplayName("schedule_add Given 정상 요청 When 부르면 Then isError=false이고 결과를 글과 structuredContent 둘 다로 돌려준다")
    void addOk() {
        // given
        given(service.add(USER, "면접", "2026-11-24T10:00:00+09:00", null))
                .willReturn(new AddResult("e_1", Occasion.formal, null, "11월 24일 오전 10시 면접을 등록했어요.", null));

        // when
        CallToolResult result = tools.scheduleAdd(USER, "면접", "2026-11-24T10:00:00+09:00", null);

        // then
        assertThat(result.isError()).isFalse();
        assertThat(body(result)).containsEntry("eventId", "e_1").containsEntry("occasion", "formal")
                .containsEntry("spokenSummary", "11월 24일 오전 10시 면접을 등록했어요.");
        assertThat(((TextContent) result.content().get(0)).text()).contains("\"eventId\":\"e_1\"");
    }

    @Test
    @DisplayName("schedule_add Given 시작 시각 형식이 틀림 When 부르면 Then isError=true, bad_request를 돌려준다")
    void addBadRequest() {
        // given
        given(service.add(USER, "면접", "내일 열 시", null))
                .willThrow(ScheduleException.badRequest("시작 시각은 ISO-8601이어야 해요."));

        // when
        CallToolResult result = tools.scheduleAdd(USER, "면접", "내일 열 시", null);

        // then
        assertThat(result.isError()).isTrue();
        assertThat(error(result)).containsEntry("code", "bad_request")
                .containsEntry("message", "시작 시각은 ISO-8601이어야 해요.");
    }

    @Test
    @DisplayName("schedule_list Given 일정이 없는 날 When 부르면 Then events가 빈 배열이다")
    void listEmpty() {
        // given
        given(service.list(USER, "2026-11-21")).willReturn(List.of());

        // when
        CallToolResult result = tools.scheduleList(USER, "2026-11-21");

        // then
        assertThat(result.isError()).isFalse();
        assertThat(body(result)).containsEntry("events", List.of());
    }

    @Test
    @DisplayName("schedule_delete Given 정상 요청 When 부르면 Then deleted=true를 돌려준다")
    void deleteOk() {
        // given
        given(service.delete(USER, "e_1")).willReturn(true);

        // when
        CallToolResult result = tools.scheduleDelete(USER, "e_1");

        // then
        assertThat(result.isError()).isFalse();
        assertThat(body(result)).containsEntry("deleted", true);
    }

    @Test
    @DisplayName("schedule_set_reminder Given 없는 일정 When 부르면 Then isError=true, not_found를 돌려준다")
    void setReminderNotFound() {
        // given
        given(service.setReminder(USER, "e_x", true, 1, null)).willThrow(ScheduleException.notFound("일정을 찾지 못했어요."));

        // when
        CallToolResult result = tools.scheduleSetReminder(USER, "e_x", true, 1, null);

        // then
        assertThat(result.isError()).isTrue();
        assertThat(error(result)).containsEntry("code", "not_found");
    }

    @Test
    @DisplayName("Given 서비스에서 예상 못 한 오류 When 부르면 Then 예외를 던지지 않고 isError=true, internal_error와 쉬운 문장을 돌려준다")
    void unexpectedFailure() {
        // given
        given(service.list(USER, null)).willThrow(new IllegalStateException("db connection reset"));

        // when
        CallToolResult result = tools.scheduleList(USER, null);

        // then: 안의 오류 문장(db connection reset)은 밖으로 내보내지 않는다
        assertThat(result.isError()).isTrue();
        assertThat(error(result)).containsEntry("code", "internal_error")
                .containsEntry("message", "일정 처리 중 오류가 났어요.");
    }
}

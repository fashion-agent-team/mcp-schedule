package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.fashionagent.schedule.event.EventRepository;
import com.fashionagent.schedule.place.PlaceInfo;
import com.fashionagent.schedule.place.PlaceLookup;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 실제로 서버를 띄우고 MCP 클라이언트(agent와 같은 방식)로 도구를 불러 봅니다. 카카오는 가짜로 바꿉니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpToolsIntegrationTest {

    @TestConfiguration
    static class FakeKakao {
        @Bean
        @Primary
        PlaceLookup fakePlaceLookup() {
            return name -> name.contains("채플")
                    ? Optional.of(PlaceInfo.of("12345678", "더채플앳청담", "가정,생활 > 예식장"))
                    : Optional.empty();
        }

        /** 오늘을 2026-11-01(한국 시각)로 고정합니다. */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-01T09:00:00+09:00"));
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    EventRepository repository;

    private final ObjectMapper json = new ObjectMapper();
    private McpSyncClient client;
    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp").build()).build();
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
        repository.deleteAll();
    }

    @Test
    void 도구_다섯_개가_보인다() {
        var tools = client.listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("schedule_add", "schedule_list", "schedule_delete", "schedule_briefing",
                        "schedule_set_reminder");
        McpSchema.Tool list = tools.stream().filter(t -> t.name().equals("schedule_list")).findFirst().orElseThrow();
        assertThat(list.annotations().readOnlyHint()).isTrue();
        assertThat(list.annotations().destructiveHint()).isFalse();
        McpSchema.Tool delete = tools.stream().filter(t -> t.name().equals("schedule_delete")).findFirst().orElseThrow();
        assertThat(delete.annotations().destructiveHint()).isTrue();
        McpSchema.Tool add = tools.stream().filter(t -> t.name().equals("schedule_add")).findFirst().orElseThrow();
        assertThat(String.valueOf(add.inputSchema().get("required"))).contains("userId", "title", "start").doesNotContain("place");
    }

    @Test
    void 추가_조회_삭제() throws Exception {
        JsonNode added = call("schedule_add", Map.of("userId", userId, "title", "친구 결혼식",
                "start", "2026-11-14T14:00:00+09:00", "place", "더채플앳청담"));
        assertThat(added.path("occasion").asString()).isEqualTo("wedding");
        assertThat(added.path("placeCategory").asString()).isEqualTo("예식장");
        assertThat(added.path("spokenSummary").asString())
                .isEqualTo("11월 14일 토요일 오후 2시, 더채플앳청담에서 친구 결혼식을 등록했어요. "
                        + "그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        assertThat(added.path("careTip").asString()).isEqualTo("그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        String eventId = added.path("eventId").asString();
        assertThat(eventId).startsWith("e_");

        JsonNode listed = call("schedule_list", Map.of("userId", userId, "date", "2026-11-14"));
        assertThat(listed.path("events")).hasSize(1);
        JsonNode event = listed.path("events").get(0);
        assertThat(event.path("start").asString()).isEqualTo("2026-11-14T14:00:00+09:00");
        assertThat(event.path("occasion").asString()).isEqualTo("wedding");

        // 다른 날짜, 다른 사람에게는 안 보임
        assertThat(call("schedule_list", Map.of("userId", userId, "date", "2026-11-15")).path("events")).isEmpty();
        assertThat(call("schedule_list", Map.of("userId", UUID.randomUUID().toString(), "date", "2026-11-14"))
                .path("events")).isEmpty();

        // REST (게이트웨이 경유로 앱이 보는 곳)
        HttpResponse<String> rest = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/schedules?date=2026-11-14"))
                        .header("X-User-Id", userId).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rest.statusCode()).isEqualTo(200);
        assertThat(json.readTree(rest.body()).path("events")).hasSize(1);

        assertThat(call("schedule_delete", Map.of("userId", userId, "eventId", eventId)).path("deleted").asBoolean())
                .isTrue();
        McpSchema.CallToolResult again = client.callTool(
                new McpSchema.CallToolRequest("schedule_delete", Map.of("userId", userId, "eventId", eventId)));
        assertThat(again.isError()).isTrue();
        assertThat(json.readTree(((McpSchema.TextContent) again.content().getFirst()).text())
                .path("error").path("code").asString()).isEqualTo("not_found");
    }

    @Test
    void 알림_설정과_여러_날_조회() throws Exception {
        String wedding = call("schedule_add", Map.of("userId", userId, "title", "친구 결혼식",
                "start", "2026-11-14T14:00:00+09:00")).path("eventId").asString();
        String meeting = call("schedule_add", Map.of("userId", userId, "title", "회의",
                "start", "2026-11-03T10:00:00+09:00")).path("eventId").asString();

        // 묻기 전: 알림 켜짐, 며칠 전은 아직 안 정함
        JsonNode before = call("schedule_list", Map.of("userId", userId, "date", "2026-11-14")).path("events").get(0);
        assertThat(before.path("remind").asBoolean()).isTrue();
        assertThat(before.path("remindDaysBefore").isNull()).isTrue();

        JsonNode set = call("schedule_set_reminder", Map.of("userId", userId, "eventId", wedding,
                "remind", true, "daysBefore", 3));
        assertThat(set.path("remind").asBoolean()).isTrue();
        assertThat(set.path("remindDaysBefore").asInt()).isEqualTo(3);
        JsonNode off = call("schedule_set_reminder", Map.of("userId", userId, "eventId", meeting, "remind", false));
        assertThat(off.path("remind").asBoolean()).isFalse();
        assertThat(off.path("remindDaysBefore").isNull()).isTrue();

        McpSchema.CallToolResult tooMany = client.callTool(new McpSchema.CallToolRequest("schedule_set_reminder",
                Map.of("userId", userId, "eventId", wedding, "daysBefore", 30)));
        assertThat(tooMany.isError()).isTrue();
        McpSchema.CallToolResult other = client.callTool(new McpSchema.CallToolRequest("schedule_set_reminder",
                Map.of("userId", UUID.randomUUID().toString(), "eventId", wedding, "remind", false)));
        assertThat(other.isError()).isTrue();

        // 앱: 오늘부터 여러 날을 한 번에
        HttpResponse<String> rest = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/v1/schedules?date=2026-11-01&endDate=2026-11-20"))
                        .header("X-User-Id", userId).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rest.statusCode()).isEqualTo(200);
        JsonNode events = json.readTree(rest.body()).path("events");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).path("title").asString()).isEqualTo("회의");
        assertThat(events.get(0).path("remind").asBoolean()).isFalse();
        assertThat(events.get(1).path("remindDaysBefore").asInt()).isEqualTo(3);

        HttpResponse<String> tooLong = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/v1/schedules?date=2026-11-01&endDate=2026-12-31"))
                        .header("X-User-Id", userId).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tooLong.statusCode()).isEqualTo(400);
    }

    @Test
    void 장소_없이_제목으로_정하고_시간대_없는_시각도_받는다() {
        JsonNode added = call("schedule_add", Map.of("userId", userId, "title", "면접",
                "start", "2026-11-04T10:00:00"));
        assertThat(added.path("occasion").asString()).isEqualTo("formal");
        assertThat(added.path("placeCategory").isNull() || added.path("placeCategory").isMissingNode()).isTrue();
        assertThat(added.path("spokenSummary").asString()).isEqualTo("11월 4일 수요일 오전 10시, 면접을 등록했어요. "
                + "그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
    }

    @Test
    void 브리핑은_도구와_REST가_같다() throws Exception {
        // 오늘은 2026-11-01(테스트 시계)
        call("schedule_add", Map.of("userId", userId, "title", "친구랑 점심", "start", "2026-11-01T12:30:00+09:00"));
        call("schedule_add", Map.of("userId", userId, "title", "면접", "start", "2026-11-03T14:00:00+09:00"));
        JsonNode b = call("schedule_briefing", Map.of("userId", userId));
        assertThat(b.path("spoken").asString()).isEqualTo("오늘은 오후 12시 30분에 친구랑 점심 일정이 있어요. "
                + "모레 11월 3일 화요일 오후 2시에 면접 일정이 있어요. "
                + "입을 옷은 세탁소에 맡기셨어요? 드라이클리닝은 보통 1~3일 걸려서 아직이면 오늘 맡기면 좋아요.");
        assertThat(b.path("today")).hasSize(1);
        assertThat(b.path("upcoming").get(0).path("occasion").asString()).isEqualTo("formal");

        HttpResponse<String> rest = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/schedules/briefing"))
                        .header("X-User-Id", userId).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rest.statusCode()).isEqualTo(200);
        assertThat(json.readTree(rest.body()).path("spoken").asString()).isEqualTo(b.path("spoken").asString());
    }

    @Test
    void 잘못된_입력은_error_모양으로() {
        McpSchema.CallToolResult r = client.callTool(new McpSchema.CallToolRequest("schedule_add",
                Map.of("userId", "not-a-uuid", "title", "면접", "start", "2026-11-04T10:00:00+09:00")));
        assertThat(r.isError()).isTrue();
        assertThat(json.readTree(((McpSchema.TextContent) r.content().getFirst()).text())
                .path("error").path("code").asString()).isEqualTo("bad_request");
        assertThat(r.structuredContent()).isNotNull();
    }

    private JsonNode call(String tool, Map<String, Object> args) {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, args));
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        return json.readTree(text);
    }
}

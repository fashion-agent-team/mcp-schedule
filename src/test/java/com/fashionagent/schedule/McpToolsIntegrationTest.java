package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    void 도구_세_개가_보인다() {
        var tools = client.listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("schedule_add", "schedule_list", "schedule_delete");
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
                .isEqualTo("11월 14일 토요일 오후 2시, 더채플앳청담에서 친구 결혼식을 등록했어요.");
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
    void 장소_없이_제목으로_정하고_시간대_없는_시각도_받는다() {
        JsonNode added = call("schedule_add", Map.of("userId", userId, "title", "면접",
                "start", "2026-11-04T10:00:00"));
        assertThat(added.path("occasion").asString()).isEqualTo("formal");
        assertThat(added.path("placeCategory").isNull() || added.path("placeCategory").isMissingNode()).isTrue();
        assertThat(added.path("spokenSummary").asString()).isEqualTo("11월 4일 수요일 오전 10시, 면접을 등록했어요.");
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

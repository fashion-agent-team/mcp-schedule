package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionagent.schedule.event.Event;
import com.fashionagent.schedule.event.EventRepository;
import com.fashionagent.schedule.notify.MorningReminder;
import com.fashionagent.schedule.notify.Notifier;
import com.fashionagent.schedule.place.PlaceInfo;
import com.fashionagent.schedule.place.PlaceLookup;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 통합 테스트: 서버 전체를 띄우고 진짜 PostgreSQL(Flyway로 schedule 스키마)에 붙여,
 * MCP 클라이언트(agent와 같은 방식)와 REST(앱이 게이트웨이를 거쳐 보는 곳)로 끝까지 불러 봅니다.
 * 서버 밖의 것만 바꿉니다: 카카오 지도 검색(가짜 PlaceLookup)과 오늘 날짜(고정 시계 2026-11-01).
 * REST 입력·응답 하나하나는 api/ScheduleControllerTest(@WebMvcTest 슬라이스)가 맡습니다.
 * 시나리오 번호는 앱 레포 specs/002-schedule/spec.md의 Acceptance Scenarios입니다.
 */
@EnabledIf("com.fashionagent.schedule.TestDatabase#available")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "schedule.reminder.cron=-", "schedule.care-reminder.cron=-" })
class ScheduleIntegrationTest {

    @TestConfiguration
    static class OutsideWorld {
        @Bean
        @Primary
        PlaceLookup fakePlaceLookup() {
            return name -> name.contains("채플")
                    ? Optional.of(PlaceInfo.of("12345678", "더채플앳청담", "가정,생활 > 예식장"))
                    : Optional.empty();
        }

        /** 알림을 실제로 보내는 대신 기록합니다. fail이 true면 보내기 실패로 답합니다. */
        @Bean
        @Primary
        RecordingNotifier recordingNotifier() {
            return new RecordingNotifier();
        }

        /** 오늘을 2026-11-01(한국 시각)로 고정합니다. */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(DEFAULT_NOW);
        }
    }

    static class RecordingNotifier implements Notifier {
        final List<String> today = new CopyOnWriteArrayList<>();
        volatile boolean fail;

        @Override
        public boolean notifyToday(Event event, String message) {
            if (fail) {
                return false;
            }
            today.add(message);
            return true;
        }

        @Override
        public boolean notifyBefore(Event event, String message) {
            return !fail;
        }
    }

    static final Instant DEFAULT_NOW = Instant.parse("2026-11-01T09:00:00+09:00");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        TestDatabase.register(r);
    }

    // MCP 도구 입력. 문자열로 키를 쓰지 않고 record를 JSON으로 바꿔 보냅니다(null 칸은 안 보냄).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddArgs(String userId, String title, String start, String place) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ListArgs(String userId, String date) {
    }

    record EventArgs(String userId, String eventId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ReminderArgs(String userId, String eventId, Boolean remind, Integer daysBefore) {
    }

    record ReminderTimeArgs(String userId, String eventId, Boolean remind, Integer daysBefore, String time) {
    }

    record UserArgs(String userId) {
    }

    @LocalServerPort
    int port;

    @Autowired
    EventRepository repository;

    @Autowired
    MorningReminder morning;

    @Autowired
    RecordingNotifier notifier;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
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
        clock.set(DEFAULT_NOW);
        notifier.today.clear();
        notifier.fail = false;
    }

    private McpSchema.CallToolResult callRaw(String tool, Object args) {
        Map<String, Object> map = json.convertValue(args, new TypeReference<Map<String, Object>>() {
        });
        return client.callTool(new McpSchema.CallToolRequest(tool, map));
    }

    private JsonNode call(String tool, Object args) {
        return json.readTree(((McpSchema.TextContent) callRaw(tool, args).content().getFirst()).text());
    }

    private String errorCode(McpSchema.CallToolResult r) {
        return json.readTree(((McpSchema.TextContent) r.content().getFirst()).text()).path("error").path("code").asString();
    }

    private HttpResponse<String> rest(String pathAndQuery, String user) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery))
                .header("X-User-Id", user).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Given 서버가 떠 있음 When MCP 도구 목록을 보면 Then 도구 다섯 개와 읽기·지우기 표시, 필수 입력이 맞다")
    void 도구_다섯_개가_보인다() {
        // when
        var tools = client.listTools().tools();

        // then
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
    @DisplayName("US1-1·US2-2 Given 로그인한 사용자 When 제목·시간·장소를 말해 등록하면 Then 저장되고 한 문장으로 확인해 주며, 장소(예식장)로 분위기를 정한다")
    void 제목_시간_장소로_등록한다() {
        // when
        JsonNode added = call("schedule_add", new AddArgs(userId, "친구 결혼식", "2026-11-14T14:00:00+09:00", "더채플앳청담"));

        // then
        assertThat(added.path("eventId").asString()).startsWith("e_");
        assertThat(added.path("occasion").asString()).isEqualTo("wedding");
        assertThat(added.path("placeCategory").asString()).isEqualTo("예식장");
        assertThat(added.path("spokenSummary").asString())
                .isEqualTo("11월 14일 토요일 오후 2시, 더채플앳청담에서 친구 결혼식을 등록했어요. "
                        + "그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        assertThat(added.path("careTip").asString()).isEqualTo("그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("US1-2 Given 장소 없이 \"면접\"(시간대 없는 시각) When 등록하면 Then 장소 없이 저장되고 제목으로 분위기(formal)를 정한다")
    void 장소_없이_제목으로_정한다() {
        // when
        JsonNode added = call("schedule_add", new AddArgs(userId, "면접", "2026-11-04T10:00:00", null));

        // then
        assertThat(added.path("occasion").asString()).isEqualTo("formal");
        assertThat(added.path("placeCategory").isNull() || added.path("placeCategory").isMissingNode()).isTrue();
        assertThat(added.path("spokenSummary").asString()).isEqualTo("11월 4일 수요일 오전 10시, 면접을 등록했어요. "
                + "그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.");
    }

    @Test
    @DisplayName("US1-3 Given 제목이 비어 있음 When 등록하면 Then 저장하지 않고 bad_request")
    void 제목이_없으면_저장하지_않는다() {
        // when
        McpSchema.CallToolResult r = callRaw("schedule_add", new AddArgs(userId, " ", "2026-11-04T10:00:00+09:00", null));

        // then
        assertThat(r.isError()).isTrue();
        assertThat(errorCode(r)).isEqualTo("bad_request");
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("US3-1·US3-3 Given 내 일정과 남의 일정 When 도구와 REST로 그날을 조회하면 Then 내 것만 시간 순서로, 두 길이 같은 결과")
    void 그날_내_일정만_시간_순서로_본다() throws Exception {
        // given
        call("schedule_add", new AddArgs(userId, "친구 결혼식", "2026-11-14T14:00:00+09:00", "더채플앳청담"));
        call("schedule_add", new AddArgs(userId, "회의", "2026-11-14T10:00:00+09:00", null));
        call("schedule_add", new AddArgs(userId, "운동", "2026-11-15T07:00:00+09:00", null));
        String other = UUID.randomUUID().toString();
        call("schedule_add", new AddArgs(other, "남의 약속", "2026-11-14T12:00:00+09:00", null));

        // when
        JsonNode byTool = call("schedule_list", new ListArgs(userId, "2026-11-14")).path("events");
        HttpResponse<String> byRest = rest("/api/v1/schedules?date=2026-11-14", userId);

        // then
        assertThat(byTool).hasSize(2);
        assertThat(byTool.get(0).path("title").asString()).isEqualTo("회의");
        assertThat(byTool.get(1).path("start").asString()).isEqualTo("2026-11-14T14:00:00+09:00");
        assertThat(byTool.get(1).path("occasion").asString()).isEqualTo("wedding");
        assertThat(byRest.statusCode()).isEqualTo(200);
        assertThat(json.readTree(byRest.body()).path("events")).isEqualTo(byTool);
    }

    @Test
    @DisplayName("US3-2 Given 그날 일정 없음 When 조회하면 Then 빈 목록")
    void 일정이_없으면_빈_목록() {
        // when
        JsonNode events = call("schedule_list", new ListArgs(userId, "2026-11-20")).path("events");

        // then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("US5-1·US5-2 Given 내 일정 When 지우면 Then 지워지고, 다시 지우거나 남이 지우면 not_found")
    void 내_일정만_지운다() {
        // given
        String eventId = call("schedule_add", new AddArgs(userId, "친구 결혼식", "2026-11-14T14:00:00+09:00", null))
                .path("eventId").asString();

        // when
        McpSchema.CallToolResult byOther = callRaw("schedule_delete", new EventArgs(UUID.randomUUID().toString(), eventId));
        JsonNode deleted = call("schedule_delete", new EventArgs(userId, eventId));
        McpSchema.CallToolResult again = callRaw("schedule_delete", new EventArgs(userId, eventId));

        // then
        assertThat(byOther.isError()).isTrue();
        assertThat(errorCode(byOther)).isEqualTo("not_found");
        assertThat(deleted.path("deleted").asBoolean()).isTrue();
        assertThat(again.isError()).isTrue();
        assertThat(errorCode(again)).isEqualTo("not_found");
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Given 일정 두 개 When 알림을 켜고(3일 전)·끄고 여러 날을 REST로 보면 Then 저장된 알림 설정이 보이고, 14일 넘게·남의 일정은 거부")
    void 알림_설정을_저장하고_여러_날을_본다() throws Exception {
        // given
        String wedding = call("schedule_add", new AddArgs(userId, "친구 결혼식", "2026-11-14T14:00:00+09:00", null))
                .path("eventId").asString();
        String meeting = call("schedule_add", new AddArgs(userId, "회의", "2026-11-03T10:00:00+09:00", null))
                .path("eventId").asString();
        JsonNode before = call("schedule_list", new ListArgs(userId, "2026-11-14")).path("events").get(0);

        // when
        JsonNode set = call("schedule_set_reminder", new ReminderArgs(userId, wedding, true, 3));
        JsonNode off = call("schedule_set_reminder", new ReminderArgs(userId, meeting, false, null));
        McpSchema.CallToolResult tooMany = callRaw("schedule_set_reminder", new ReminderArgs(userId, wedding, null, 30));
        McpSchema.CallToolResult other = callRaw("schedule_set_reminder",
                new ReminderArgs(UUID.randomUUID().toString(), wedding, false, null));
        HttpResponse<String> range = rest("/api/v1/schedules?date=2026-11-01&endDate=2026-11-20", userId);
        HttpResponse<String> tooLong = rest("/api/v1/schedules?date=2026-11-01&endDate=2026-12-31", userId);

        // then
        assertThat(before.path("remind").asBoolean()).isTrue();
        assertThat(before.path("remindDaysBefore").isNull()).isTrue();
        assertThat(set.path("remindDaysBefore").asInt()).isEqualTo(3);
        assertThat(off.path("remind").asBoolean()).isFalse();
        assertThat(off.path("remindDaysBefore").isNull()).isTrue();
        assertThat(tooMany.isError()).isTrue();
        assertThat(other.isError()).isTrue();
        assertThat(range.statusCode()).isEqualTo(200);
        JsonNode events = json.readTree(range.body()).path("events");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).path("title").asString()).isEqualTo("회의");
        assertThat(events.get(0).path("remind").asBoolean()).isFalse();
        assertThat(events.get(1).path("remindDaysBefore").asInt()).isEqualTo(3);
        assertThat(tooLong.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Given 29일 오후 8시 일정 When \"2일 전 오후 4시에 알려 줘\"로 알림을 정하면 "
            + "Then remindTime 16:00이 저장되고, 시각을 비우면 null(아침 8시), 잘못된 시각은 거부")
    void 알림_시각을_정한다() throws Exception {
        // given
        String party = call("schedule_add", new AddArgs(userId, "친구 모임", "2026-11-29T20:00:00+09:00", null))
                .path("eventId").asString();

        // when
        JsonNode at4 = call("schedule_set_reminder", new ReminderTimeArgs(userId, party, true, 2, "16:00"));
        HttpResponse<String> listed = rest("/api/v1/schedules?date=2026-11-29", userId);
        JsonNode shortHour = call("schedule_set_reminder", new ReminderTimeArgs(userId, party, true, 1, "9:30"));
        McpSchema.CallToolResult bad = callRaw("schedule_set_reminder",
                new ReminderTimeArgs(userId, party, true, 2, "오후 4시"));
        JsonNode noTime = call("schedule_set_reminder", new ReminderArgs(userId, party, true, 3));
        JsonNode off = call("schedule_set_reminder", new ReminderTimeArgs(userId, party, false, null, "16:00"));

        // then
        assertThat(at4.path("remindDaysBefore").asInt()).isEqualTo(2);
        assertThat(at4.path("remindTime").asString()).isEqualTo("16:00");
        assertThat(json.readTree(listed.body()).path("events").get(0).path("remindTime").asString()).isEqualTo("16:00");
        assertThat(shortHour.path("remindTime").asString()).isEqualTo("09:30");
        assertThat(bad.isError()).isTrue();
        assertThat(noTime.path("remindTime").isNull()).isTrue();
        assertThat(off.path("remindTime").isNull()).isTrue();
    }

    @Test
    @DisplayName("Given 오늘(11-01) 점심과 모레 면접 When 도구와 REST로 브리핑을 받으면 Then 같은 문장을 받는다")
    void 브리핑은_도구와_REST가_같다() throws Exception {
        // given
        call("schedule_add", new AddArgs(userId, "친구랑 점심", "2026-11-01T12:30:00+09:00", null));
        call("schedule_add", new AddArgs(userId, "면접", "2026-11-03T14:00:00+09:00", null));

        // when
        JsonNode b = call("schedule_briefing", new UserArgs(userId));
        HttpResponse<String> byRest = rest("/api/v1/schedules/briefing", userId);

        // then
        assertThat(b.path("spoken").asString()).isEqualTo("오늘은 오후 12시 30분에 친구랑 점심 일정이 있어요. "
                + "모레 11월 3일 화요일 오후 2시에 면접 일정이 있어요. "
                + "입을 옷은 세탁소에 맡기셨어요? 드라이클리닝은 보통 1~3일 걸려서 아직이면 오늘 맡기면 좋아요.");
        assertThat(b.path("today")).hasSize(1);
        assertThat(b.path("upcoming").get(0).path("occasion").asString()).isEqualTo("formal");
        assertThat(byRest.statusCode()).isEqualTo(200);
        assertThat(json.readTree(byRest.body()).path("spoken").asString()).isEqualTo(b.path("spoken").asString());
    }

    @Test
    @DisplayName("Given 사용자 id가 UUID가 아님 When 도구를 부르면 Then error JSON(bad_request)과 structuredContent")
    void 잘못된_입력은_error_모양으로() {
        // when
        McpSchema.CallToolResult r = callRaw("schedule_add", new AddArgs("not-a-uuid", "면접", "2026-11-04T10:00:00+09:00", null));

        // then
        assertThat(r.isError()).isTrue();
        assertThat(errorCode(r)).isEqualTo("bad_request");
        assertThat(r.structuredContent()).isNotNull();
    }

    @Test
    @DisplayName("US4-1 Given 오늘 일정이 있는 사용자 When 아침 8시가 되면 Then 알림이 한 번 가고 보낸 시각이 기록된다")
    void 오늘_일정은_아침에_한_번_알린다() {
        // given
        clock.set(Instant.parse("2026-11-01T07:00:00+09:00"));
        call("schedule_add", new AddArgs(userId, "친구 결혼식", "2026-11-01T14:00:00+09:00", "더채플앳청담"));
        clock.set(Instant.parse("2026-11-01T08:00:00+09:00"));

        // when
        morning.sendTodayReminders();

        // then
        assertThat(notifier.today).containsExactly("오늘 오후 2시에 더채플앳청담에서 친구 결혼식 일정이 있어요. 옷 추천을 받아 보세요.");
        assertThat(repository.findAll()).allSatisfy(e -> assertThat(e.getNotifiedAt()).isNotNull());
    }

    @Test
    @DisplayName("US4-2 Given 알림 작업이 같은 날 두 번 돌아감 When 다시 실행되면 Then 이미 보낸 일정은 다시 보내지 않는다")
    void 같은_날_두_번_돌아도_한_번만() {
        // given
        clock.set(Instant.parse("2026-11-01T07:00:00+09:00"));
        call("schedule_add", new AddArgs(userId, "친구랑 점심", "2026-11-01T12:30:00+09:00", null));
        clock.set(Instant.parse("2026-11-01T08:00:00+09:00"));
        morning.sendTodayReminders();

        // when
        morning.sendTodayReminders();

        // then
        assertThat(notifier.today).hasSize(1);
    }

    @Test
    @DisplayName("US4-3 Given 오늘 8시 이후에 등록한 오늘 일정 When 알림 작업이 돌면 Then 아침 알림은 보내지 않는다")
    void 아침_이후에_등록한_오늘_일정은_알리지_않는다() {
        // given (기본 시계: 2026-11-01 오전 9시)
        call("schedule_add", new AddArgs(userId, "친구랑 저녁", "2026-11-01T19:00:00+09:00", null));

        // when
        morning.sendTodayReminders();

        // then
        assertThat(notifier.today).isEmpty();
    }

    @Test
    @DisplayName("US4-4 Given 알림 전송 실패 When 실패하면 Then 보낸 것으로 기록하지 않고 다음에 다시 시도한다")
    void 보내기에_실패하면_기록하지_않는다() {
        // given
        clock.set(Instant.parse("2026-11-01T07:00:00+09:00"));
        call("schedule_add", new AddArgs(userId, "면접", "2026-11-01T14:00:00+09:00", null));
        clock.set(Instant.parse("2026-11-01T08:00:00+09:00"));
        notifier.fail = true;

        // when
        morning.sendTodayReminders();
        notifier.fail = false;
        morning.sendTodayReminders();

        // then
        assertThat(notifier.today).hasSize(1);
    }

    @Test
    @DisplayName("005 US1-2 Given Flyway가 schedule 스키마를 만듦 When 외래 키를 보면 Then 다른 스키마를 가리키는 FK는 0개")
    void schedule_스키마는_다른_스키마로_FK가_없다() {
        // when
        Long crossSchema = jdbc.sql("""
                SELECT count(*) FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                JOIN pg_class ref ON ref.oid = c.confrelid
                WHERE c.contype = 'f' AND n.nspname = 'schedule' AND ref.relnamespace <> c.connamespace""")
                .query(Long.class).single();

        // then
        assertThat(crossSchema).isZero();
    }
}

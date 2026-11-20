package com.fashionagent.schedule.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fashionagent.schedule.common.ScheduleException;
import com.fashionagent.schedule.event.EventView;
import com.fashionagent.schedule.event.Occasion;
import com.fashionagent.schedule.event.ScheduleService;

/**
 * ScheduleController 슬라이스 테스트(@WebMvcTest). 일정 서비스(ScheduleService)는 정상 동작한다고 보고 Mock으로 바꿉니다.
 * 컨트롤러가 맡은 일(X-User-Id·쿼리 받기, 응답 모양, 오류를 약속한 모양으로 바꾸기)만 확인합니다.
 * 일정이 실제로 저장·정렬되는지는 ScheduleIntegrationTest(진짜 PostgreSQL)가 봅니다.
 * 시나리오 번호는 앱 레포 specs/002-schedule/spec.md의 Acceptance Scenarios입니다.
 */
@WebMvcTest(controllers = ScheduleController.class)
class ScheduleControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ScheduleService service;

    final String userId = UUID.randomUUID().toString();

    static EventView event(String title, String start, String place, String category, Occasion occasion) {
        return new EventView("e_" + title.hashCode(), title, start, place, category, occasion, true, null, null);
    }

    ResultActions list(String user, String date) throws Exception {
        return mvc.perform(get("/api/v1/schedules").header("X-User-Id", user).param("date", date));
    }

    @Test
    @DisplayName("US3-1 Given 오늘 일정 두 개 When 오늘을 조회하면 Then 서비스가 준 순서 그대로 장소·장소 분류·분위기를 포함해 받는다")
    void 오늘_일정을_받는다() throws Exception {
        // given
        given(service.list(userId, "2026-11-14", null)).willReturn(List.of(
                event("회의", "2026-11-14T10:00:00+09:00", null, null, Occasion.business),
                event("친구 결혼식", "2026-11-14T14:00:00+09:00", "더채플앳청담", "예식장", Occasion.wedding)));

        // when
        ResultActions result = list(userId, "2026-11-14");

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].title").value("회의"))
                .andExpect(jsonPath("$.events[1].start").value("2026-11-14T14:00:00+09:00"))
                .andExpect(jsonPath("$.events[1].place").value("더채플앳청담"))
                .andExpect(jsonPath("$.events[1].placeCategory").value("예식장"))
                .andExpect(jsonPath("$.events[1].occasion").value("wedding"))
                .andExpect(jsonPath("$.events[1].remind").value(true));
    }

    @Test
    @DisplayName("US3-2 Given 오늘 일정 없음 When 조회하면 Then 빈 목록을 받는다")
    void 일정이_없으면_빈_목록() throws Exception {
        // given
        given(service.list(userId, "2026-11-15", null)).willReturn(List.of());

        // when
        ResultActions result = list(userId, "2026-11-15");

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.events").isEmpty());
    }

    @Test
    @DisplayName("US3-3 Given 게이트웨이가 붙인 X-User-Id When 조회하면 Then 그 사용자 id로만 서비스에 묻는다")
    void 헤더의_사용자_id로만_조회한다() throws Exception {
        // given
        given(service.list(anyString(), any(), any())).willReturn(List.of());

        // when
        list(userId, "2026-11-14");

        // then
        then(service).should().list(userId, "2026-11-14", null);
    }

    @Test
    @DisplayName("US3-3 Given X-User-Id가 없음 When 조회하면 Then 401 unauthorized이고 서비스는 부르지 않는다")
    void 사용자_id가_없으면_401() throws Exception {
        // when
        ResultActions result = mvc.perform(get("/api/v1/schedules").param("date", "2026-11-14"));

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("unauthorized"));
        then(service).should(never()).list(any(), any(), any());
    }

    @Test
    @DisplayName("Given 앱이 여러 날을 한 번에 봄 When date·endDate로 조회하면 Then 두 값을 그대로 서비스에 넘긴다")
    void 여러_날을_한_번에_조회한다() throws Exception {
        // given
        given(service.list(userId, "2026-11-01", "2026-11-20")).willReturn(List.of(
                event("회의", "2026-11-03T10:00:00+09:00", null, null, Occasion.business)));

        // when
        ResultActions result = mvc.perform(get("/api/v1/schedules").header("X-User-Id", userId)
                .param("date", "2026-11-01").param("endDate", "2026-11-20"));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    @DisplayName("Given 31일을 넘는 기간 When 조회하면 Then 서비스가 낸 400을 {error:{code,message}} 모양으로 돌려준다")
    void 서비스_오류는_약속한_모양으로() throws Exception {
        // given
        given(service.list(userId, "2026-11-01", "2026-12-31"))
                .willThrow(ScheduleException.badRequest("기간은 31일까지예요."));

        // when
        ResultActions result = mvc.perform(get("/api/v1/schedules").header("X-User-Id", userId)
                .param("date", "2026-11-01").param("endDate", "2026-12-31"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("bad_request"))
                .andExpect(jsonPath("$.error.message").value("기간은 31일까지예요."));
    }

    @Test
    @DisplayName("Given 오늘 일정과 3일 안 중요한 일정 When 브리핑을 조회하면 Then 읽어 줄 문장(spoken)과 두 목록을 받는다")
    void 브리핑을_받는다() throws Exception {
        // given
        given(service.briefing(userId)).willReturn(new ScheduleService.Briefing("오늘은 오후 12시 30분에 친구랑 점심 일정이 있어요.",
                List.of(event("친구랑 점심", "2026-11-01T12:30:00+09:00", null, null, Occasion.casual)),
                List.of(event("면접", "2026-11-03T14:00:00+09:00", null, null, Occasion.formal)),
                List.of()));

        // when
        ResultActions result = mvc.perform(get("/api/v1/schedules/briefing").header("X-User-Id", userId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.spoken").value("오늘은 오후 12시 30분에 친구랑 점심 일정이 있어요."))
                .andExpect(jsonPath("$.today.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].occasion").value("formal"));
    }
}

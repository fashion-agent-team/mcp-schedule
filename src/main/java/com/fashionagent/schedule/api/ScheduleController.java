package com.fashionagent.schedule.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fashionagent.schedule.common.ApiError;
import com.fashionagent.schedule.common.ScheduleException;
import com.fashionagent.schedule.event.EventView;
import com.fashionagent.schedule.event.ScheduleService;

/**
 * 앱이 게이트웨이를 거쳐 직접 보는 날짜별 일정(openapi.yaml의 GET /api/v1/schedules).
 * 사용자는 게이트웨이가 붙여 준 X-User-Id로 정합니다.
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    public record ListResponse(List<EventView> events) {
    }

    @GetMapping
    public ListResponse list(@RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String date) {
        return new ListResponse(service.list(userId, date));
    }

    /** 앱을 열었을 때 인사 뒤에 읽어 줄 오늘 일정과 3일 안 중요한 일정. spoken을 그대로 읽으면 됩니다. */
    @GetMapping("/briefing")
    public ScheduleService.Briefing briefing(@RequestHeader("X-User-Id") String userId) {
        return service.briefing(userId);
    }

    @ExceptionHandler(ScheduleException.class)
    ResponseEntity<ApiError> handle(ScheduleException e) {
        return ResponseEntity.status(e.status()).body(e.toApiError());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> missingUser(MissingRequestHeaderException e) {
        return ResponseEntity.status(401).body(ApiError.of("unauthorized", "X-User-Id가 없어요."));
    }
}

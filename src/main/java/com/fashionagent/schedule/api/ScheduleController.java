package com.fashionagent.schedule.api;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
 * 사용자는 게이트웨이가 그대로 넘긴 로그인 토큰({@code Authorization: Bearer})을 여기서 확인해 정합니다({@link AccessTokens}).
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService service;
    private final AccessTokens tokens;

    public ScheduleController(ScheduleService service, AccessTokens tokens) {
        this.service = service;
        this.tokens = tokens;
    }

    public record ListResponse(List<EventView> events) {
    }

    /** date 하루, 또는 endDate까지(포함, 최대 31일). 앱은 알림을 맞출 때 여러 날을 한 번에 봅니다. */
    @GetMapping
    public ListResponse list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String endDate) {
        return new ListResponse(service.list(tokens.userId(authorization), date, endDate));
    }

    /** 앱을 열었을 때 인사 뒤에 읽어 줄 오늘 일정과 3일 안 중요한 일정. spoken을 그대로 읽으면 됩니다. */
    @GetMapping("/briefing")
    public ScheduleService.Briefing briefing(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.briefing(tokens.userId(authorization));
    }

    @ExceptionHandler(ScheduleException.class)
    ResponseEntity<ApiError> handle(ScheduleException e) {
        return ResponseEntity.status(e.status()).body(e.toApiError());
    }
}

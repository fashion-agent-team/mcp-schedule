package com.fashionagent.schedule.common;

import org.springframework.http.HttpStatus;

/** 도구와 REST가 같은 오류 코드를 쓰도록 하는 예외. */
public class ScheduleException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ScheduleException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ScheduleException badRequest(String message) {
        return new ScheduleException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static ScheduleException notFound(String message) {
        return new ScheduleException(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public ApiError toApiError() {
        return ApiError.of(code, getMessage());
    }
}

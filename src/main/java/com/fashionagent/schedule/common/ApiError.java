package com.fashionagent.schedule.common;

/** 약속 문서 4절의 오류 모양: {"error": {"code", "message"}} */
public record ApiError(Body error) {

    public record Body(String code, String message) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(new Body(code, message));
    }
}

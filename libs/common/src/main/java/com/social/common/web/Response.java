package com.social.common.web;

import lombok.Data;

@Data
public class Response<T> {
    private int status;
    private String message;
    private T data;

    private Response(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> Response<T> ok(T data) {
        return new Response<>(200, "OK", data);
    }
}

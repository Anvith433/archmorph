package com.demo.common;

public class ApiResponse<T> {
    public final T data;

    public ApiResponse(T data) {
        this.data = data;
    }
}

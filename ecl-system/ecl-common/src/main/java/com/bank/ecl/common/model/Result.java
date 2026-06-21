package com.bank.ecl.common.model;

/**
 * Unified API response wrapper.
 */
public class Result<T> {

    private String code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Success with data.
     */
    public static <T> Result<T> success(T data) {
        return new Result<>("200", "success", data);
    }

    /**
     * Success without data.
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * Error with code and message.
     */
    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

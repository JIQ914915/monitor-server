package com.lzzh.monitor.common.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/** 统一响应体（对接前端 api/request.ts 解包约定：code===0 视为成功）。 */
@Schema(description = "统一响应体")
public class Result<T> implements Serializable {

    @Schema(description = "业务状态码，0 表示成功", example = "0")
    private int code;

    @Schema(description = "提示信息", example = "success")
    private String msg;

    @Schema(description = "业务数据")
    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.code(), ResultCode.SUCCESS.msg(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return new Result<>(rc.code(), rc.msg(), null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

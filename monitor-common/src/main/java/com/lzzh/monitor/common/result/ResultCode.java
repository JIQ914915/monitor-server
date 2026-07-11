package com.lzzh.monitor.common.result;

/** 统一返回码。0 表示成功，其余为各类业务/系统错误。 */
public enum ResultCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    BUSINESS_ERROR(500, "业务处理失败"),
    SYSTEM_ERROR(999, "系统异常");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int code() {
        return code;
    }

    public String msg() {
        return msg;
    }
}

package com.lzzh.monitor.common.exception;

import com.lzzh.monitor.common.result.ResultCode;

/** 业务异常：由全局异常处理器转为统一返回体。 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.code();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode rc) {
        super(rc.msg());
        this.code = rc.code();
    }

    public int getCode() {
        return code;
    }
}

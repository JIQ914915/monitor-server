package com.lzzh.monitor.admin.security;

import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前登录用户工具。 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}

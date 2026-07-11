package com.lzzh.monitor.admin.security;

import com.lzzh.monitor.common.constant.Constants;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.ResultCode;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.List;

/** @RequiresPerm 权限校验切面：取当前用户权限并集，校验是否包含所需权限码。 */
@Aspect
@Component
public class PermissionAspect {

    @Pointcut("@annotation(com.lzzh.monitor.admin.security.RequiresPerm)")
    public void perm() {
    }

    @Before("perm() && @annotation(requiresPerm)")
    public void check(RequiresPerm requiresPerm) {
        LoginUser user = SecurityUtils.current();
        List<String> perms = user.permissions();
        String required = requiresPerm.value();
        boolean ok = perms.contains(Constants.PERM_ALL) || perms.contains(required);
        if (!ok) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}

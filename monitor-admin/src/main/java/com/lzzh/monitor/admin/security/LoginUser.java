package com.lzzh.monitor.admin.security;

import java.util.List;

/** 当前登录用户（多角色并集，§5.5）。 */
public record LoginUser(Long id, String username, String nickname,
                        List<String> roles, List<String> permissions) {
}

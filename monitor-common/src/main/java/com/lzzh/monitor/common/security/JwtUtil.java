package com.lzzh.monitor.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** JWT 生成与解析工具。 */
@Component
public class JwtUtil {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 以用户名为主体签发令牌，userId 放入自定义 claim。 */
    public String generate(Long userId, String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getExpireMillis());
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从原始请求头值中剥离前缀得到纯 token。 */
    public String resolve(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String prefix = props.getPrefix();
        return headerValue.startsWith(prefix) ? headerValue.substring(prefix.length()) : headerValue;
    }
}

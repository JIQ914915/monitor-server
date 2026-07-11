package com.lzzh.monitor.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 配置（application.yml: monitor.jwt.*）。 */
@ConfigurationProperties(prefix = "monitor.jwt")
public class JwtProperties {

    /** HMAC 密钥（生产从环境变量/密管注入，长度需 ≥32 字节）。 */
    private String secret = "change-me-please-change-me-please-32bytes";

    /** 过期毫秒，默认 8 小时。 */
    private long expireMillis = 8 * 60 * 60 * 1000L;

    /** 请求头名称。 */
    private String header = "Authorization";

    /** 令牌前缀。 */
    private String prefix = "Bearer ";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireMillis() {
        return expireMillis;
    }

    public void setExpireMillis(long expireMillis) {
        this.expireMillis = expireMillis;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}

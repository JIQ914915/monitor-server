package com.lzzh.monitor.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 采集账号密码对称加解密（AES-256-GCM，§13.3.2）。
 * <p>存储格式：{@code enc:<base64(iv(12B) || ciphertext+tag)>}；前缀用于识别密文，
 * 未带前缀的值按历史明文处理（返回原值），保证旧数据平滑过渡且避免重复加密。
 * <p>密钥来自配置 {@code monitor.crypto.password-key}（任意字符串，经 SHA-256 派生 32 字节）。
 * 生产环境务必通过环境变量/密管覆盖默认值。
 */
@Component
public class PasswordCipher {

    private static final Logger log = LoggerFactory.getLogger(PasswordCipher.class);

    /** 密文前缀标识。 */
    private static final String ENC_PREFIX = "enc:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /** 默认开发密钥（生产必须覆盖）。 */
    private static final String DEFAULT_KEY = "change-me-monitor-crypto-key-please-override";

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public PasswordCipher(@Value("${monitor.crypto.password-key:}") String configuredKey) {
        String raw = (configuredKey == null || configuredKey.isBlank()) ? DEFAULT_KEY : configuredKey;
        if (DEFAULT_KEY.equals(raw)) {
            log.warn("采集密码加密使用内置默认密钥，请在生产环境通过 monitor.crypto.password-key 覆盖！");
        }
        this.keySpec = new SecretKeySpec(sha256(raw), "AES");
    }

    /**
     * 加密明文密码。空值原样返回；已是密文（带前缀）则不重复加密。
     *
     * @param plain 明文
     * @return 密文令牌（enc: 前缀）；入参为空或已加密时原样返回
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || plain.startsWith(ENC_PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("采集密码加密失败", e);
        }
    }

    /**
     * 解密密码令牌。空值原样返回；无前缀（历史明文）原样返回。
     *
     * @param token 密文令牌或历史明文
     * @return 明文
     */
    public String decrypt(String token) {
        if (token == null || token.isEmpty() || !token.startsWith(ENC_PREFIX)) {
            return token;
        }
        try {
            byte[] data = Base64.getDecoder().decode(token.substring(ENC_PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("采集密码解密失败", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密密钥失败", e);
        }
    }
}

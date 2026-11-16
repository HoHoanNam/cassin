package com.cassin.auth.dto;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình cassin.refresh-token.* trong application.yml.
 *
 * @param ttl          thời gian sống của MỘT refresh token. Mỗi lần refresh cấp token mới với hạn mới
 *                     → không mở app quá ttl thì phải đăng nhập lại.
 * @param cookieSecure cookie chỉ gửi qua HTTPS. Bật khi deploy; tắt khi dev (http://localhost).
 */
@ConfigurationProperties(prefix = "cassin.refresh-token")
public record RefreshTokenProperties(Duration ttl, boolean cookieSecure) {}

package com.cassin.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Dùng để đọc SECRET & ACCESS_TOKEN_TTL trong application.yml
// Gom cấu hình cassin.jwt.* trong application.yml thành một object có kiểu rõ ràng.
// "1h" trong yml được Spring tự đổi thành Duration.
@ConfigurationProperties(prefix = "cassin.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {}

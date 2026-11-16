package com.cassin.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các job chạy theo lịch (@Scheduled), vd RefreshTokenCleanup.
 * Thiếu annotation này thì @Scheduled bị bỏ qua, không báo lỗi gì.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}

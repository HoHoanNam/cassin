package com.cassin.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Một nguồn thời gian chung cho cả app.
 * - Đúng giờ VN: server đặt ở nước ngoài (UTC) thì 6 giờ sáng ở VN vẫn đang là "hôm qua" ở UTC.
 * - Test được: trong test, thay Clock thật bằng Clock.fixed(...) để "đóng băng" một ngày.
 */
@Configuration
public class TimeConfig {
  public static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Bean
  public Clock clock() {
    return Clock.system(APP_ZONE);
  }
}
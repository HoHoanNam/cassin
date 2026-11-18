package com.cassin.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.cassin.common.TimeConfig;

/**
 * [THÊM] Đưa MutableClock vào context.
 *
 * Đặt TÊN KHÁC "clock": Boot mặc định cấm ghi đè bean trùng tên (BeanDefinitionOverrideException).
 * @Primary: service nào cần Clock (DeckService, ReviewService, RefreshTokenService...) đều nhận cái này.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock(TimeConfig.APP_ZONE);
  }
}
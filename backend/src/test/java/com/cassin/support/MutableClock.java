package com.cassin.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * [THÊM] Đồng hồ "tua được" cho integration test.
 *
 * App lấy giờ qua bean Clock (TimeConfig). Test thay bằng đồng hồ này để kiểm tra những luật
 * phụ thuộc thời gian mà không phải Thread.sleep: vd cửa sổ chạy đua 10 giây của refresh token.
 *
 *   reset()            → đứng ở "bây giờ" thật, gọi trong @BeforeEach
 *   advance(11 giây)   → nhảy tới tương lai, không chờ
 */
public class MutableClock extends Clock {

  private final ZoneId zone;
  private volatile Instant now = Instant.now(); // volatile: request MockMvc và test có thể khác luồng

  public MutableClock(ZoneId zone) {
    this.zone = zone;
  }

  public void reset() {
    now = Instant.now();
  }

  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId other) {
    return Clock.fixed(now, other);
  }
}
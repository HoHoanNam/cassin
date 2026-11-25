package com.cassin.badge.rule;

import java.time.Clock;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Chim sớm (SPECIAL, bí mật): ôn thẻ trong khung 04:00 – 05:59 (trước 6 giờ sáng, sau khung Cú đêm). Một lần. */
@Component
public class EarlyBirdRule extends ReviewHourRule {

  public EarlyBirdRule(JdbcClient jdbc, Clock clock) {
    super(jdbc, clock);
  }

  @Override
  public String code() {
    return "EARLY_BIRD";
  }

  @Override
  protected List<Integer> hours() {
    return List.of(4, 5);
  }
}

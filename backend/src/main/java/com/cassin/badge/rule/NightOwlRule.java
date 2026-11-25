package com.cassin.badge.rule;

import java.time.Clock;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cú đêm (SPECIAL, bí mật): ôn thẻ trong khung 23:00 – 03:59. Một lần. */
@Component
public class NightOwlRule extends ReviewHourRule {

  public NightOwlRule(JdbcClient jdbc, Clock clock) {
    super(jdbc, clock);
  }

  @Override
  public String code() {
    return "NIGHT_OWL";
  }

  @Override
  protected List<Integer> hours() {
    return List.of(23, 0, 1, 2, 3);
  }
}

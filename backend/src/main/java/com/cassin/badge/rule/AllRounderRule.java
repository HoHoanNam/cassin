package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.practice.ReviewAnsweredEvent;

import lombok.RequiredArgsConstructor;

/**
 * Đa năng (SPECIAL): đã thử đủ 3 kiểu luyện (lật thẻ, điền từ, xếp câu). Mốc 3 kiểu, một lần.
 *
 * Tiến độ = số kiểu KHÁC NHAU từng dùng (0 → 3), nên trang Thành tích hiện được "2/3 kiểu".
 */
@Component
@RequiredArgsConstructor
public class AllRounderRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "ALL_ROUNDER";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReviewAnsweredEvent.class);
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("select count(distinct mode) from review_events where user_id = :userId")
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

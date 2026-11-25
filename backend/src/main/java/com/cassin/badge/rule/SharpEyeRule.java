package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.report.ReportResolvedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Mắt tinh (COMMUNITY): số báo lỗi nội dung của mình được admin xác nhận (RESOLVED). Mốc 1 / 5 / 20.
 *
 * Báo lỗi bị từ chối (REJECTED) hoặc đang chờ (OPEN) không tính. Người nhận là NGƯỜI BÁO, sự kiện phát từ thao tác
 * của admin → ReportResolvedEvent.userId() đã là người báo (xem BadgeTrigger).
 * Index idx_reports_reporter_created (reporter_id, created_at DESC) phục vụ câu đếm này.
 */
@Component
@RequiredArgsConstructor
public class SharpEyeRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "SHARP_EYE";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReportResolvedEvent.class);
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("select count(*) from content_reports where reporter_id = :userId and status = 'RESOLVED'")
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

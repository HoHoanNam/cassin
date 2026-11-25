package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.practice.ReviewAnsweredEvent;

import lombok.RequiredArgsConstructor;

/**
 * Chăm chỉ (REVIEW): tổng số lượt trả lời, mọi chế độ (lật thẻ + điền từ + xếp câu). Mốc 30 / 300 / 1500 trong V18.
 *
 * Đếm cả câu SAI: huy hiệu thưởng sự chăm chỉ, không phải độ chính xác.
 * Đếm thẳng từ bảng nên lượt trả lời có TRƯỚC khi có huy hiệu cũng được tính (người đã ôn 500 lượt nhận luôn
 * cấp I và II ở lần trả lời kế tiếp; popup chỉ hiện cấp II).
 *
 * Vì sao JdbcClient mà không thêm method vào ReviewEventRepository? Rule chỉ ĐỌC một con số; viết SQL ngay tại đây
 * giữ cả luật lẫn câu truy vấn trong một file, và module practice không phải biết gì về huy hiệu.
 * Index idx_review_events_user_time (user_id, reviewed_at DESC) có user_id đứng đầu → câu đếm dùng được index.
 */
@Component
@RequiredArgsConstructor
public class DiligentRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "DILIGENT";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReviewAnsweredEvent.class);
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("select count(*) from review_events where user_id = :userId")
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

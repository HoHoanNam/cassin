package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.practice.ReviewAnsweredEvent;

import lombok.RequiredArgsConstructor;

/**
 * Trở lại (SPECIAL): quay lại học sau một quãng nghỉ từ 7 ngày trở lên. Một lần.
 *
 * Đọc study_days (mỗi ngày có ôn thẻ = 1 dòng). lag() lấy ngày học LIỀN TRƯỚC của từng ngày học:
 *   học 01/11, rồi 09/11 → 09/11 - 01/11 = 8 → nghỉ trọn 7 ngày (02 → 08) → đạt.
 *   học 01/11, rồi 08/11 → 7 → chỉ nghỉ 6 ngày → chưa.
 * Ngày học đầu tiên không có ngày trước (lag = null, phép trừ ra null) → không bao giờ tính là "trở lại".
 * Chỉ ôn thẻ lật mới đánh dấu ngày học → chỉ nghe FLASHCARD.
 */
@Component
@RequiredArgsConstructor
public class ComebackRule implements BadgeRule {
  /** Hiệu hai ngày học liền nhau >= 8 ⇔ nghỉ trọn >= 7 ngày ở giữa. */
  static final int MIN_GAP_DAYS = 8;

  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "COMEBACK";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReviewAnsweredEvent.class);
  }

  @Override
  public boolean listensTo(BadgeTrigger event) {
    return event instanceof ReviewAnsweredEvent answered && answered.isFlashcard();
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("""
            select count(*) from (
              select study_date - lag(study_date) over (order by study_date) as gap
              from study_days
              where user_id = :userId
            ) d
            where d.gap >= :minGap
            """)
        .param("userId", userId)
        .param("minGap", MIN_GAP_DAYS)
        .query(Long.class)
        .single();
  }
}

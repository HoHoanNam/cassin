package com.cassin.badge.rule;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.practice.ReviewAnsweredEvent;

/**
 * Phần chung của Cú đêm và Chim sớm: đếm lượt ÔN THẺ LẬT rơi vào một số giờ trong ngày.
 *
 * Giờ tính theo múi giờ của bean Clock (TimeConfig.APP_ZONE = Asia/Ho_Chi_Minh), cùng cách study_days và thống kê
 * theo ngày đang tính: reviewed_at lưu mốc tuyệt đối (timestamptz), "at time zone" đổi ra giờ đồng hồ ở VN rồi mới
 * lấy giờ. Khi nào có múi giờ riêng cho từng người dùng thì chỉ cần đổi :zone ở đây.
 *
 * Hai khung giờ không chồng nhau: 2 giờ sáng là "thức khuya" (Cú đêm), không phải "dậy sớm".
 */
abstract class ReviewHourRule implements BadgeRule {
  private final JdbcClient jdbc;
  private final Clock clock;

  ReviewHourRule(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Các giờ (0 – 23, giờ đồng hồ ở múi giờ của app) được tính. */
  protected abstract List<Integer> hours();

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReviewAnsweredEvent.class);
  }

  @Override
  public boolean listensTo(BadgeTrigger event) {
    return event instanceof ReviewAnsweredEvent answered && answered.isFlashcard();
  }

  /** in (:hours): JdbcClient tự bung List thành (?, ?, ?...). */
  @Override
  public long progress(Long userId) {
    return jdbc.sql("""
            select count(*) from review_events
            where user_id = :userId
              and mode = 'FLASHCARD'
              and cast(extract(hour from reviewed_at at time zone :zone) as integer) in (:hours)
            """)
        .param("userId", userId)
        .param("zone", clock.getZone().getId())
        .param("hours", hours())
        .query(Long.class)
        .single();
  }
}

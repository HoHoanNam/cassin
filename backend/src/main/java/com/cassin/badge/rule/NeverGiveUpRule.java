package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.practice.ReviewAnsweredEvent;
import com.cassin.vocab.CardProgress;

import lombok.RequiredArgsConstructor;

/**
 * Không bỏ cuộc (SPECIAL): có ít nhất một nghĩa đã quên từ 3 lần trở lên mà vẫn đưa được lên hộp 5. Một lần.
 *
 * lapse_count chỉ tăng, không bao giờ giảm (CardProgress.review) → "từng quên >= 3 lần" đọc thẳng từ cột này.
 * Chỉ ôn thẻ lật mới đổi hộp / lapse_count → chỉ nghe FLASHCARD.
 */
@Component
@RequiredArgsConstructor
public class NeverGiveUpRule implements BadgeRule {
  static final int MIN_LAPSES = 3;

  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "NEVER_GIVE_UP";
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
            select count(*) from card_progresses
            where user_id = :userId and box = :box and lapse_count >= :minLapses
            """)
        .param("userId", userId)
        .param("box", CardProgress.MAX_BOX)
        .param("minLapses", MIN_LAPSES)
        .query(Long.class)
        .single();
  }
}

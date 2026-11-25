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
 * Nhớ lâu (MASTERY): số nghĩa ĐANG ở hộp 5 (đã thuộc). Mốc 5 / 50 / 300 nghĩa.
 *
 * Đếm hiện trạng: quên một nghĩa ở hộp 5 → nghĩa đó về hộp 1, tiến độ giảm 1; cấp đã đạt vẫn giữ.
 * Chỉ ôn thẻ lật mới đổi hộp (luyện điền từ / xếp câu sai chỉ dời hạn ôn, CardProgress.dueSoon) → chỉ nghe FLASHCARD.
 */
@Component
@RequiredArgsConstructor
public class MemoryKeeperRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "MEMORY_KEEPER";
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
    return jdbc.sql("select count(*) from card_progresses where user_id = :userId and box = :box")
        .param("userId", userId)
        .param("box", CardProgress.MAX_BOX)
        .query(Long.class)
        .single();
  }
}

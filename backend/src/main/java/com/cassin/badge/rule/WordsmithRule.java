package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.practice.ReviewAnsweredEvent;

import lombok.RequiredArgsConstructor;

/**
 * Thợ rèn câu (PRACTICE): số câu điền từ + xếp câu đã trả lời (đúng hay sai đều tính). Mốc 30 / 300 / 1000 câu.
 *
 * Khác Chăm chỉ ở chỗ KHÔNG tính thẻ lật: huy hiệu này dành cho phần luyện bằng câu ví dụ.
 * Chỉ nghe lượt không phải FLASHCARD (ôn thẻ lật không đổi con số này).
 * mode IN (...) viết thẳng trong SQL: hai giá trị này khớp CHECK của review_events (V10) và enum PracticeMode.
 */
@Component
@RequiredArgsConstructor
public class WordsmithRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "WORDSMITH";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReviewAnsweredEvent.class);
  }

  @Override
  public boolean listensTo(BadgeTrigger event) {
    return event instanceof ReviewAnsweredEvent answered && !answered.isFlashcard();
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("""
            select count(*) from review_events
            where user_id = :userId and mode in ('FILL_BLANK', 'SENTENCE_BUILD')
            """)
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

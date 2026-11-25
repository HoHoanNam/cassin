package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.quiz.QuizSubmittedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Nhà ngữ pháp (QUIZ): số CHỦ ĐỀ khác nhau đã nộp ít nhất một bài (điểm bao nhiêu cũng được). Mốc 3 / 6 / 10 chủ đề.
 *
 * count(distinct topic_id): làm đi làm lại một chủ đề không tăng tiến độ, huy hiệu thưởng việc học RỘNG.
 * Lượt bắt đầu mà chưa nộp (submitted_at null) không tính.
 */
@Component
@RequiredArgsConstructor
public class GrammarianRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "GRAMMARIAN";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(QuizSubmittedEvent.class);
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("""
            select count(distinct topic_id) from quiz_attempts
            where user_id = :userId and submitted_at is not null
            """)
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

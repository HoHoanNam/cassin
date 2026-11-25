package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.quiz.QuizSubmittedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Điểm tuyệt đối (QUIZ): số lượt kiểm tra ngữ pháp đúng hết. Mốc 1 / 5 / 20 bài.
 *
 * Đếm theo LƯỢT, không theo chủ đề: mỗi lượt là 10 câu bốc ngẫu nhiên trong chủ đề, làm lại vẫn là đề khác.
 * Chỉ nghe bài đúng hết: bài có câu sai không thể làm con số này tăng, khỏi truy vấn.
 */
@Component
@RequiredArgsConstructor
public class PerfectionistRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "PERFECTIONIST";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(QuizSubmittedEvent.class);
  }

  @Override
  public boolean listensTo(BadgeTrigger event) {
    return event instanceof QuizSubmittedEvent submitted && submitted.isPerfect();
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("""
            select count(*) from quiz_attempts
            where user_id = :userId and submitted_at is not null and score = total
            """)
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

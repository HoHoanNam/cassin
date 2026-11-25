package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.passage.ReadingSubmittedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Tự lực (READING): số bài đọc khác nhau có ít nhất một lượt ĐÚNG HẾT mà KHÔNG mở gợi ý nào. Mốc 1 / 5 / 15 bài.
 *
 * "Không mở gợi ý" = không câu nào của lượt đó có reading_answers.hints_used > 0 (NOT EXISTS). Lượt đúng hết nhưng
 * có mở gợi ý thì không tính; làm lại bài đó mà không mở gợi ý thì tính.
 * Chỉ nghe lượt đúng hết không gợi ý: lượt khác không thể làm con số này tăng.
 */
@Component
@RequiredArgsConstructor
public class SelfReliantRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "SELF_RELIANT";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReadingSubmittedEvent.class);
  }

  @Override
  public boolean listensTo(BadgeTrigger event) {
    return event instanceof ReadingSubmittedEvent submitted && submitted.isPerfectWithoutHints();
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("""
            select count(distinct a.passage_id) from reading_attempts a
            where a.user_id = :userId
              and a.submitted_at is not null
              and a.score = a.total
              and not exists (select 1 from reading_answers r where r.attempt_id = a.id and r.hints_used > 0)
            """)
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

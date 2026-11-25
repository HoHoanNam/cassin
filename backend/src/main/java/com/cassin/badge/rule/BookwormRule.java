package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.passage.ReadingSubmittedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Mọt sách (READING): số BÀI ĐỌC khác nhau đã nộp (điểm bao nhiêu cũng được). Mốc 3 / 10 / 30 bài.
 *
 * count(distinct passage_id): đọc lại một bài không tính thêm (khác Điểm tuyệt đối của ngữ pháp, nơi mỗi lượt là
 * một đề ngẫu nhiên khác). Bài đọc thì làm lại vẫn là đúng bài đó.
 */
@Component
@RequiredArgsConstructor
public class BookwormRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "BOOKWORM";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(ReadingSubmittedEvent.class);
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("""
            select count(distinct passage_id) from reading_attempts
            where user_id = :userId and submitted_at is not null
            """)
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

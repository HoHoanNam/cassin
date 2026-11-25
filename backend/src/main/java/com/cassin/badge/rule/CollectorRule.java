package com.cassin.badge.rule;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.vocab.WordsAddedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Nhà sưu tầm (COLLECTION): số từ trong sổ từ CỦA MÌNH (words.owner_id = người học). Mốc 20 / 100 / 300 từ.
 *
 * Từ hệ thống (owner_id NULL) không tính. Xóa từ thì tiến độ giảm, cấp đã đạt vẫn giữ.
 * Câu đếm dùng được index uq_words_owner_term (owner_id, lower(term)): owner_id là cột đầu.
 */
@Component
@RequiredArgsConstructor
public class CollectorRule implements BadgeRule {
  private final JdbcClient jdbc;

  @Override
  public String code() {
    return "COLLECTOR";
  }

  @Override
  public Set<Class<? extends BadgeTrigger>> triggers() {
    return Set.of(WordsAddedEvent.class);
  }

  @Override
  public long progress(Long userId) {
    return jdbc.sql("select count(*) from words where owner_id = :userId")
        .param("userId", userId)
        .query(Long.class)
        .single();
  }
}

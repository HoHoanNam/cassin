package com.cassin.badge.rule;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.cassin.badge.BadgeRule;
import com.cassin.badge.event.BadgeTrigger;
import com.cassin.practice.ReviewAnsweredEvent;
import com.cassin.vocab.StudyDayRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lửa bền (STREAK): chuỗi ngày học liên tiếp. Mốc 3 / 14 / 60 ngày.
 *
 * Tiến độ = chuỗi HIỆN TẠI (giống trang Ôn tập), không phải chuỗi dài nhất từng có: đứt chuỗi thì thanh tiến độ về
 * lại, nhưng cấp đã đạt vẫn giữ (user_badges không bao giờ bị xóa). Muốn lên cấp sau phải giữ được một chuỗi mới đủ dài.
 *
 * Chỉ ôn thẻ lật mới đánh dấu "hôm nay có học" (study_days, ReviewService.review) → chỉ nghe lượt FLASHCARD.
 * Lượt điền từ / xếp câu không đổi chuỗi, xét lại chỉ tốn một truy vấn vô ích.
 */
@Component
@RequiredArgsConstructor
public class OnFireRule implements BadgeRule {
  private final StudyDayRepository studyDayRepository;
  private final Clock clock;

  @Override
  public String code() {
    return "ON_FIRE";
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
    return studyDayRepository.currentStreak(userId, LocalDate.now(clock));
  }
}

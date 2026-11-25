package com.cassin.practice;

import com.cassin.badge.event.BadgeTrigger;

/**
 * Phát sau MỖI lượt trả lời hợp lệ (đã ghi một dòng review_events): ôn thẻ lật (ReviewService) và luyện điền từ /
 * xếp câu (PracticeService). Lượt bị từ chối (409, 400) không phát.
 *
 * Mang theo mode và correct để rule tự lọc mà không phải truy vấn: Chăm chỉ đếm mọi lượt, Lửa bền / Nhớ lâu chỉ quan
 * tâm thẻ lật, Thợ rèn câu chỉ quan tâm điền từ + xếp câu.
 */
public record ReviewAnsweredEvent(Long userId, PracticeMode mode, boolean correct) implements BadgeTrigger {

  /** Lượt ôn thẻ lật (chấm hộp Leitner, đánh dấu ngày học). */
  public boolean isFlashcard() {
    return mode == PracticeMode.FLASHCARD;
  }
}

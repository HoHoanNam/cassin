package com.cassin.passage;

import com.cassin.badge.event.BadgeTrigger;

/**
 * Phát khi nộp bài đọc THÀNH CÔNG (PassageService.submit). Nộp lại (409) hay câu trả lời sai dữ liệu (400) không phát.
 * Huy hiệu nghe: Mọt sách (mọi bài), Tự lực (bài đúng hết mà không mở gợi ý nào).
 *
 * @param correctWithoutHints số câu đúng mà KHÔNG mở gợi ý (ReadingAttempt.correctWithoutHints)
 */
public record ReadingSubmittedEvent(Long userId, Long passageId, int score, int total, long correctWithoutHints)
    implements BadgeTrigger {

  /** Đúng hết, và không câu nào phải mở gợi ý. */
  public boolean isPerfectWithoutHints() {
    return correctWithoutHints == total;
  }
}

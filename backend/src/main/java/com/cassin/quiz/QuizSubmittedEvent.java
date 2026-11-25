package com.cassin.quiz;

import com.cassin.badge.event.BadgeTrigger;

/**
 * Phát khi nộp bài kiểm tra ngữ pháp THÀNH CÔNG (QuizService.submit). Nộp lần 2 (409) hay dữ liệu sai (400) không phát.
 * Huy hiệu nghe: Điểm tuyệt đối (chỉ bài đúng hết), Nhà ngữ pháp (mọi bài).
 */
public record QuizSubmittedEvent(Long userId, Long topicId, int score, int total) implements BadgeTrigger {

  /** Đúng hết mọi câu trong đề. */
  public boolean isPerfect() {
    return score == total;
  }
}

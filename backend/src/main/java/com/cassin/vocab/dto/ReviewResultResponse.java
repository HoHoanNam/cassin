package com.cassin.vocab.dto;

import java.time.LocalDate;

import com.cassin.vocab.CardProgress;

/** Kết quả một lần ôn: hộp mới, ngày ôn tiếp theo. [V10] cardId → senseId. */
public record ReviewResultResponse(Long senseId, int box, LocalDate dueDate, int reviewCount, int lapseCount) {
  public static ReviewResultResponse from(CardProgress p) {
    return new ReviewResultResponse(p.getSense().getId(), p.getBox(), p.getDueDate(), p.getReviewCount(), p.getLapseCount());
  }
}

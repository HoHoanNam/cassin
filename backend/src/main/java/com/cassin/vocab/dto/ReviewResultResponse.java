package com.cassin.vocab.dto;

import java.time.LocalDate;

import com.cassin.vocab.CardProgress;

// Kết quả sau khi chấm: frontend hiện "Lên hộp 3, ôn lại sau 4 ngày".
public record ReviewResultResponse(Long cardId, int box, LocalDate dueDate, int reviewCount, int lapseCount) {
  public static ReviewResultResponse from(CardProgress p) {
    return new ReviewResultResponse(
      p.getCard().getId(), 
      p.getBox(), 
      p.getDueDate(), 
      p.getReviewCount(), 
      p.getLapseCount()
    );
  }
}


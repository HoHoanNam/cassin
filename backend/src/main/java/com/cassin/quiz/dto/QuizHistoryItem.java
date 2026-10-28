package com.cassin.quiz.dto;

import java.time.Instant;

import com.cassin.quiz.QuizAttempt;

// score / submittedAt có thể null (lượt bắt đầu nhưng chưa nộp) → dùng Integer, không dùng int.
public record QuizHistoryItem(
  Long attemptId, 
  Long topicId, 
  String topicTitle,
  Integer score, 
  int total, 
  Instant startedAt, 
  Instant submittedAt
) {
  public static QuizHistoryItem from(QuizAttempt a) {
    return new QuizHistoryItem(
      a.getId(), 
      a.getTopic().getId(), 
      a.getTopic().getTitle(),
      a.getScore(), 
      a.getTotal(), 
      a.getStartedAt(), 
      a.getSubmittedAt()
    );
  }
}

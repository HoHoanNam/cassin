package com.cassin.quiz.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.cassin.quiz.AttemptAnswer;
import com.cassin.quiz.QuizAttempt;

// Kết quả sau khi nộp bài.
public record QuizResultResponse(
  Long attemptId, 
  Long topicId, 
  String topicTitle,
  int score, 
  int total, 
  Instant submittedAt, 
  List<AnswerResult> answers
) {
  public static QuizResultResponse from(QuizAttempt attempt) {
    List<AnswerResult> answers = new ArrayList<>();
    for (AttemptAnswer answer : attempt.getAnswers()) {
      answers.add(AnswerResult.from(answer));
    }
    return new QuizResultResponse(
      attempt.getId(), 
      attempt.getTopic().getId(), 
      attempt.getTopic().getTitle(),
      attempt.getScore(), 
      attempt.getTotal(), 
      attempt.getSubmittedAt(), 
      answers
    );
  }
}

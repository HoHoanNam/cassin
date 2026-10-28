package com.cassin.quiz.dto;

import java.util.ArrayList;
import java.util.List;

import com.cassin.quiz.AttemptAnswer;
import com.cassin.quiz.QuizAttempt;


public record QuizStartResponse(
  Long attemptId, 
  Long topicId, 
  String topicTitle, 
  int total,
  List<QuizQuestionResponse> questions
) {
  public static QuizStartResponse from(QuizAttempt attempt) {
    List<QuizQuestionResponse> questions = new ArrayList<>();
    for (AttemptAnswer answer : attempt.getAnswers()) {
      questions.add(QuizQuestionResponse.from(answer));
    }
    return new QuizStartResponse(
      attempt.getId(),
      attempt.getTopic().getId(),
      attempt.getTopic().getTitle(),
      attempt.getTotal(),
      questions
    );
  }
}

package com.cassin.quiz.dto;

import java.util.ArrayList;
import java.util.List;

import com.cassin.grammar.Question;
import com.cassin.grammar.QuestionOption;
import com.cassin.quiz.AttemptAnswer;

public record QuizQuestionResponse(
  Long questionId, 
  int position, 
  String content, 
  List<OptionResponse> options
) {
  public static QuizQuestionResponse from(AttemptAnswer answer) {
    Question q = answer.getQuestion();
    List<OptionResponse> options = new ArrayList<>();
    for (QuestionOption option : q.getOptions()) {   // đã đúng thứ tự 0..3 nhờ @OrderBy
      options.add(OptionResponse.from(option));
    }
    return new QuizQuestionResponse(q.getId(), answer.getPosition(), q.getContent(), options);
  }
}

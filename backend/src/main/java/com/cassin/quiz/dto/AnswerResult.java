package com.cassin.quiz.dto;

import java.util.ArrayList;
import java.util.List;

import com.cassin.grammar.Question;
import com.cassin.grammar.QuestionOption;
import com.cassin.quiz.AttemptAnswer;

// Một câu trong KẾT QUẢ: lúc này mới được lộ đáp án đúng + lời giải thích.
public record AnswerResult(
  Long questionId, 
  int position, 
  String content, 
  List<OptionResponse> options,
  Long selectedOptionId, 
  Long correctOptionId, 
  boolean correct, 
  String explanation
) {
  public static AnswerResult from(AttemptAnswer answer) {
    Question q = answer.getQuestion();
    List<OptionResponse> options = new ArrayList<>();
    Long correctOptionId = null;
    for (QuestionOption option : q.getOptions()) {
      options.add(OptionResponse.from(option));
      if (option.isCorrect()) {
        correctOptionId = option.getId();
      }
    }
    // selectedOption có thể null (bỏ trống) → kiểm tra trước khi lấy id.
    Long selectedOptionId = answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId();
    return new AnswerResult(
      q.getId(),
      answer.getPosition(), 
      q.getContent(), 
      options,
      selectedOptionId, 
      correctOptionId, 
      answer.isCorrect(), 
      q.getExplanation()
    );
  }
}

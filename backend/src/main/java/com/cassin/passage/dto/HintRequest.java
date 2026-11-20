package com.cassin.passage.dto;

import com.cassin.passage.HintInput;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** { "questionPhrase": "replacement", "passagePhrase": "alternative" } */
public record HintRequest(
    @NotBlank(message = "Vui lòng nhập cụm từ trong câu hỏi") @Size(max = 100) String questionPhrase,
    @NotBlank(message = "Vui lòng nhập cụm đồng nghĩa trong bài") @Size(max = 100) String passagePhrase) {

  public HintInput toInput() {
    return new HintInput(questionPhrase, passagePhrase);
  }
}

package com.cassin.passage;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** True / False / Not Given: prompt là một câu nhận định, học viên đối chiếu với bài. */
@Entity
@DiscriminatorValue("TFNG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TfngQuestion extends ReadingQuestion {

  // SINGLE_TABLE: cột riêng của lớp con phải cho phép NULL (dòng MCQ / BLANK để trống cột này).
  // "Bắt buộc với TFNG" do factory bên dưới và CHECK trong V17 bảo đảm.
  @Enumerated(EnumType.STRING)
  @Column(name = "tfng_answer", length = 10)
  private TfngAnswer answer;

  public static TfngQuestion create(Passage passage, int position, QuestionContent content,
      TfngAnswer answer, Instant now) {
    if (answer == null) {
      throw new IllegalArgumentException(label(position) + "vui lòng chọn True / False / Not Given");
    }
    TfngQuestion question = new TfngQuestion();
    question.init(passage, position, content, now);
    question.answer = answer;
    return question;
  }

  @Override
  public QuestionType type() {
    return QuestionType.TFNG;
  }

  @Override
  public GradeResult grade(AnswerInput input) {
    return input.tfng() == null ? GradeResult.blank() : GradeResult.of(input.tfng() == answer);
  }
}

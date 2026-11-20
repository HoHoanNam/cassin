package com.cassin.passage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Trắc nghiệm A/B/C/D: đúng 4 lựa chọn, đúng 1 lựa chọn đúng. */
@Entity
@DiscriminatorValue("MCQ")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class McqQuestion extends ReadingQuestion {
  public static final int OPTION_COUNT = 4;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<ReadingOption> options = new ArrayList<>();

  public static McqQuestion create(Passage passage, int position, QuestionContent content,
      List<ChoiceInput> choices, Instant now) {
    McqQuestion question = new McqQuestion();
    question.init(passage, position, content, now);
    question.replaceOptions(choices);
    return question;
  }

  @Override
  public QuestionType type() {
    return QuestionType.MCQ;
  }

  @Override
  public GradeResult grade(AnswerInput input) {
    if (input.optionId() == null) {
      return GradeResult.blank();
    }
    ReadingOption chosen = options.stream()
        .filter(o -> o.getId().equals(input.optionId()))
        .findFirst()
        // id lựa chọn của câu khác: dữ liệu sai (400), không phải "trả lời sai".
        .orElseThrow(() -> new IllegalArgumentException(label(getPosition()) + "lựa chọn không thuộc câu hỏi này"));
    return GradeResult.option(chosen);
  }

  public List<ReadingOption> getOptions() {
    return Collections.unmodifiableList(options);
  }

  private void replaceOptions(List<ChoiceInput> choices) {
    String label = label(getPosition());
    if (choices == null || choices.size() != OPTION_COUNT) {
      throw new IllegalArgumentException(label + "phải có đúng " + OPTION_COUNT + " lựa chọn");
    }
    if (choices.stream().filter(c -> c != null && c.correct()).count() != 1) {
      throw new IllegalArgumentException(label + "phải có đúng 1 lựa chọn đúng");
    }
    Set<String> seen = new HashSet<>();
    options.clear();
    for (ChoiceInput choice : choices) {
      if (choice == null || isBlank(choice.content())) {
        throw new IllegalArgumentException(label + "lựa chọn không được để trống");
      }
      String text = choice.content().strip();
      if (!seen.add(text.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException(label + "hai lựa chọn không được trùng nhau");
      }
      options.add(new ReadingOption(this, options.size(), text, choice.correct()));
    }
  }
}

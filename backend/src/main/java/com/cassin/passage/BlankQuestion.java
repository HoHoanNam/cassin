package com.cassin.passage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Điền vào chỗ trống dựa theo bài đọc. Prompt có đúng một "____".
 * Chấm 2 bước (BlankAnswerRules): đúng định dạng chưa → có khớp một đáp án được chấp nhận không.
 */
@Entity
@DiscriminatorValue("BLANK")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlankQuestion extends ReadingQuestion {
  public static final int MAX_ACCEPTED_ANSWERS = 5;

  @Enumerated(EnumType.STRING)
  @Column(name = "answer_kind", length = 20)
  private AnswerKind answerKind;

  // NULL khi answerKind = NUMBER.
  @Column(name = "max_words")
  private Integer maxWords;

  // Danh sách chuỗi đơn giản thuộc hẳn về câu hỏi → @ElementCollection, không cần entity riêng.
  @ElementCollection
  @CollectionTable(name = "reading_blank_answers", joinColumns = @JoinColumn(name = "question_id"))
  @OrderColumn(name = "position")
  @Column(name = "answer", nullable = false, length = 100)
  private List<String> acceptedAnswers = new ArrayList<>();

  public static BlankQuestion create(Passage passage, int position, QuestionContent content,
      AnswerKind kind, Integer maxWords, List<String> acceptedAnswers, Instant now) {
    BlankQuestion question = new BlankQuestion();
    question.init(passage, position, content, now);
    question.applyRules(kind, maxWords, acceptedAnswers);
    return question;
  }

  @Override
  public QuestionType type() {
    return QuestionType.BLANK;
  }

  @Override
  public GradeResult grade(AnswerInput input) {
    String normalized = BlankAnswerRules.normalize(input.text());
    if (normalized.isEmpty()) {
      return GradeResult.blank();
    }
    RejectedReason reason = BlankAnswerRules.check(answerKind, maxWords, input.text());
    if (reason != null) {
      return GradeResult.rejected(reason);
    }
    return GradeResult.of(acceptedAnswers.stream().map(BlankAnswerRules::normalize).anyMatch(normalized::equals));
  }

  /** "NO MORE THAN TWO WORDS AND/OR A NUMBER" */
  public String instruction() {
    return BlankAnswerRules.instruction(answerKind, maxWords);
  }

  public List<String> getAcceptedAnswers() {
    return Collections.unmodifiableList(acceptedAnswers);
  }

  private void applyRules(AnswerKind kind, Integer maxWords, List<String> answers) {
    String label = label(getPosition());
    if (getPrompt().split(BlankAnswerRules.BLANK_MARKER, -1).length - 1 != 1) {
      throw new IllegalArgumentException(
          label + "đề phải có đúng một chỗ trống \"" + BlankAnswerRules.BLANK_MARKER + "\"");
    }
    if (kind == null) {
      throw new IllegalArgumentException(label + "vui lòng chọn loại đáp án");
    }
    if (kind == AnswerKind.NUMBER) {
      maxWords = null; // "A NUMBER" không giới hạn số từ
    } else if (maxWords == null || maxWords < 1 || maxWords > 3) {
      throw new IllegalArgumentException(label + "số từ tối đa phải từ 1 đến 3");
    }
    if (answers == null || answers.isEmpty() || answers.size() > MAX_ACCEPTED_ANSWERS) {
      throw new IllegalArgumentException(label + "cần từ 1 đến " + MAX_ACCEPTED_ANSWERS + " đáp án được chấp nhận");
    }
    Set<String> seen = new HashSet<>();
    for (String answer : answers) {
      if (isBlank(answer)) {
        throw new IllegalArgumentException(label + "đáp án không được để trống");
      }
      // Đáp án chuẩn cũng phải đúng giới hạn, nếu không sẽ không ai làm đúng được câu này.
      if (BlankAnswerRules.check(kind, maxWords, answer) != null) {
        throw new IllegalArgumentException(label + "đáp án \"" + answer.strip() + "\" không đúng yêu cầu "
            + BlankAnswerRules.instruction(kind, maxWords));
      }
      if (!seen.add(BlankAnswerRules.normalize(answer))) {
        throw new IllegalArgumentException(label + "hai đáp án không được trùng nhau");
      }
    }
    this.answerKind = kind;
    this.maxWords = maxWords;
    this.acceptedAnswers.clear();
    answers.forEach(a -> this.acceptedAnswers.add(a.strip()));
  }
}

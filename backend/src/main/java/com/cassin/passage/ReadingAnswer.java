package com.cassin.passage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Câu trả lời cho MỘT câu trong một lượt làm. Mỗi dạng câu dùng một cột; cả ba NULL = bỏ trống. */
@Entity
@Table(name = "reading_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "attempt_id", nullable = false)
  private ReadingAttempt attempt;

  // LAZY → getQuestion() có thể trả về PROXY của ReadingQuestion (xem ghi chú ở ReadingQuestion).
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private ReadingQuestion question;

  @Column(nullable = false)
  private int position;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "selected_option_id")
  private ReadingOption selectedOption;

  @Enumerated(EnumType.STRING)
  @Column(name = "tfng_answer", length = 10)
  private TfngAnswer tfngAnswer;

  @Column(name = "text_answer", length = 100)
  private String textAnswer;

  @Column(nullable = false)
  private boolean correct;

  @Enumerated(EnumType.STRING)
  @Column(name = "rejected_reason", length = 30)
  private RejectedReason rejectedReason;

  @Column(name = "hints_used", nullable = false)
  private int hintsUsed;

  ReadingAnswer(ReadingAttempt attempt, ReadingQuestion question, int position) {
    this.attempt = attempt;
    this.question = question;
    this.position = position;
  }

  void useHint() {
    hintsUsed++;
  }

  /** Lưu đúng một cột theo dạng câu, cùng kết quả chấm. */
  void record(AnswerInput input, GradeResult result) {
    QuestionType type = question.type();
    this.selectedOption = result.selectedOption();
    this.tfngAnswer = type == QuestionType.TFNG ? input.tfng() : null;
    this.textAnswer = type == QuestionType.BLANK && input.text() != null && !input.text().isBlank()
        ? input.text().strip()
        : null;
    this.correct = result.correct();
    this.rejectedReason = result.rejectedReason();
  }
}

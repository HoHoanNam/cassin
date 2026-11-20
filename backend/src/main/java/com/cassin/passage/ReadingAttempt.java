package com.cassin.passage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cassin.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một lượt làm bài đọc. Lúc bắt đầu tạo sẵn MỖI câu một ReadingAnswer (còn trống),
 * để ghi được số gợi ý đã mở ngay cả khi học viên chưa trả lời câu đó.
 */
@Entity
@Table(name = "reading_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "passage_id", nullable = false)
  private Passage passage;

  @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<ReadingAnswer> answers = new ArrayList<>();

  // NULL khi chưa nộp (CHECK trong V17).
  private Integer score;

  @Column(nullable = false)
  private int total;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  public static ReadingAttempt start(User user, Passage passage, Instant now) {
    if (!passage.isPublished()) {
      throw new IllegalStateException("Bài đọc chưa được publish");
    }
    ReadingAttempt attempt = new ReadingAttempt();
    attempt.user = user;
    attempt.passage = passage;
    attempt.startedAt = now;
    for (ReadingQuestion question : passage.getQuestions()) {
      attempt.answers.add(new ReadingAnswer(attempt, question, attempt.answers.size()));
    }
    attempt.total = attempt.answers.size();
    return attempt;
  }

  public boolean isOwnedBy(Long userId) {
    return user.getId().equals(userId);
  }

  public boolean isSubmitted() {
    return submittedAt != null;
  }

  /** Mở gợi ý KẾ TIẾP của một câu; rỗng nếu câu đó đã hết gợi ý. */
  public Optional<ReadingHint> revealNextHint(Long questionId) {
    requireNotSubmitted();
    ReadingAnswer answer = answerOf(questionId);
    Optional<ReadingHint> hint = answer.getQuestion().hintAt(answer.getHintsUsed());
    hint.ifPresent(h -> answer.useHint());
    return hint;
  }

  /**
   * Nộp bài. Câu không có trong inputs = bỏ trống.
   *
   * @param inputs key = id câu hỏi
   */
  public void submit(Map<Long, AnswerInput> inputs, Instant now) {
    requireNotSubmitted();
    inputs.keySet().forEach(this::answerOf); // id câu lạ → IllegalArgumentException
    int correct = 0;
    for (ReadingAnswer answer : answers) {
      AnswerInput input = inputs.getOrDefault(answer.getQuestion().getId(), AnswerInput.empty());
      // grade() gọi qua proxy LAZY vẫn chạy đúng code của lớp con thật (đa hình qua proxy).
      answer.record(input, answer.getQuestion().grade(input));
      if (answer.isCorrect()) {
        correct++;
      }
    }
    this.score = correct;
    this.submittedAt = now;
  }

  /** "5 câu đúng không cần gợi ý" */
  public long correctWithoutHints() {
    return answers.stream().filter(a -> a.isCorrect() && a.getHintsUsed() == 0).count();
  }

  public List<ReadingAnswer> getAnswers() {
    return Collections.unmodifiableList(answers);
  }

  public ReadingAnswer answerOf(Long questionId) {
    return answers.stream()
        .filter(a -> a.getQuestion().getId().equals(questionId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Câu hỏi không thuộc lượt làm bài này"));
  }

  private void requireNotSubmitted() {
    if (isSubmitted()) {
      throw new IllegalStateException("Lượt làm bài đã nộp");
    }
  }
}

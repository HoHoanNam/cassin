package com.cassin.passage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lớp cha của 3 dạng câu hỏi. Cả 3 nằm CHUNG bảng reading_questions (SINGLE_TABLE);
 * Hibernate đọc cột question_type để biết tạo McqQuestion, TfngQuestion hay BlankQuestion.
 *
 * Mỗi lớp con tự cài grade(...) → ReadingAttempt gọi question.grade(input) mà không cần biết dạng câu (đa hình).
 *
 * KHÔNG khai báo sealed: Hibernate tạo proxy LAZY bằng cách sinh lớp con lúc chạy, lớp sealed không cho làm vậy.
 * Cũng vì proxy mà không dùng instanceof trực tiếp trên câu hỏi lấy qua quan hệ LAZY
 * (proxy của ReadingQuestion không phải McqQuestion) → PassageMapper gọi Hibernate.unproxy trước.
 */
@Entity
@Table(name = "reading_questions")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "question_type", length = 10)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ReadingQuestion {
  public static final int MAX_HINTS = 3;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "passage_id", nullable = false)
  private Passage passage;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String prompt;

  // Câu trích NGUYÊN VĂN trong bài; FE tô sáng sau khi nộp.
  @Column(nullable = false, columnDefinition = "TEXT")
  private String evidence;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String explanation;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<ReadingHint> hints = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public abstract QuestionType type();

  /** Chấm một câu trả lời. Input sai (vd. optionId của câu khác) → IllegalArgumentException. */
  public abstract GradeResult grade(AnswerInput input);

  /** Lớp con gọi trong factory của mình để gán phần chung. */
  protected void init(Passage passage, int position, QuestionContent content, Instant now) {
    String label = label(position);
    if (passage == null) {
      throw new IllegalArgumentException("Câu hỏi phải thuộc một bài đọc");
    }
    if (content == null || isBlank(content.prompt())) {
      throw new IllegalArgumentException(label + "vui lòng nhập đề");
    }
    if (isBlank(content.evidence())) {
      throw new IllegalArgumentException(label + "vui lòng chọn câu bằng chứng trong bài");
    }
    if (isBlank(content.explanation())) {
      throw new IllegalArgumentException(label + "vui lòng nhập lời giải thích");
    }
    this.passage = passage;
    this.position = position;
    this.prompt = content.prompt().strip();
    this.evidence = content.evidence().strip();
    this.explanation = content.explanation().strip();
    this.createdAt = now;
    this.updatedAt = now;
    replaceHints(content.hints() == null ? List.of() : content.hints());
  }

  /**
   * Bằng chứng và gợi ý phải trỏ vào chữ CÓ THẬT trong bài. Passage gọi lại mỗi khi nội dung bài đổi,
   * để sửa bài không làm gãy câu hỏi đã soạn.
   */
  void checkAgainst(String passageContent) {
    if (!passageContent.contains(evidence)) {
      throw new IllegalArgumentException(label(position) + "câu bằng chứng phải trích nguyên văn từ bài đọc");
    }
    String lowerContent = passageContent.toLowerCase(Locale.ROOT);
    for (ReadingHint hint : hints) {
      if (!lowerContent.contains(hint.getPassagePhrase().toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException(
            label(position) + "không tìm thấy \"" + hint.getPassagePhrase() + "\" trong bài đọc");
      }
    }
  }

  /** Gợi ý thứ index (0, 1, 2); rỗng nếu đã hết. */
  public Optional<ReadingHint> hintAt(int index) {
    return index < hints.size() ? Optional.of(hints.get(index)) : Optional.empty();
  }

  public List<ReadingHint> getHints() {
    return Collections.unmodifiableList(hints);
  }

  private void replaceHints(List<HintInput> inputs) {
    String label = label(position);
    if (inputs.size() > MAX_HINTS) {
      throw new IllegalArgumentException(label + "tối đa " + MAX_HINTS + " gợi ý");
    }
    String lowerPrompt = prompt.toLowerCase(Locale.ROOT);
    Set<String> seen = new HashSet<>();
    hints.clear();
    for (HintInput input : inputs) {
      if (input == null || isBlank(input.questionPhrase()) || isBlank(input.passagePhrase())) {
        throw new IllegalArgumentException(label + "gợi ý phải có đủ cụm trong câu hỏi và cụm trong bài");
      }
      String questionPhrase = input.questionPhrase().strip();
      String passagePhrase = input.passagePhrase().strip();
      // "replacement ≈ alternative" chỉ có nghĩa khi "replacement" nằm trong câu hỏi.
      if (!lowerPrompt.contains(questionPhrase.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException(label + "không tìm thấy \"" + questionPhrase + "\" trong câu hỏi");
      }
      if (questionPhrase.equalsIgnoreCase(passagePhrase)) {
        throw new IllegalArgumentException(label + "gợi ý phải là hai cách diễn đạt khác nhau");
      }
      if (!seen.add(questionPhrase.toLowerCase(Locale.ROOT) + "|" + passagePhrase.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException(label + "hai gợi ý không được trùng nhau");
      }
      hints.add(new ReadingHint(this, hints.size(), questionPhrase, passagePhrase));
    }
  }

  /** "Câu 3: " — đứng đầu mọi thông báo lỗi để admin biết sửa câu nào. */
  protected static String label(int position) {
    return "Câu " + (position + 1) + ": ";
  }

  protected static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}

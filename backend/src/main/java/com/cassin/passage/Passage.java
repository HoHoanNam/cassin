package com.cassin.passage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cassin.grammar.CefrLevel;
import com.cassin.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một bài đọc và các câu hỏi của nó. Passage là aggregate root của nhóm
 * Passage → ReadingQuestion → (ReadingOption, ReadingHint): mọi thay đổi câu hỏi đi qua đây để
 *   - câu bằng chứng (evidence) và cụm đồng nghĩa trong gợi ý luôn CÓ THẬT trong bài
 *   - bài đã publish không quay về nháp; bài đã lưu trữ không sửa được
 *
 * Luật "bài đã có người làm thì khóa" cần đếm reading_attempts → nằm ở AdminPassageService.
 */
@Entity
@Table(name = "passages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Passage {
  public static final int MAX_CONTENT = 5000;
  public static final int MAX_QUESTIONS = 20;
  /** Tốc độ đọc trung bình của người học (từ/phút), để hiện "~3 phút đọc". */
  public static final int WORDS_PER_MINUTE = 150;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 150)
  private String title;

  // Xuống dòng 2 lần = sang đoạn mới; FE tách đoạn để đánh số Paragraph A, B, C...
  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 2)
  private CefrLevel level;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PassageStatus status;

  @Column(name = "word_count", nullable = false)
  private int wordCount;

  // NULL khi tài khoản admin tạo bài đã bị xóa (ON DELETE SET NULL).
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @OneToMany(mappedBy = "passage", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<ReadingQuestion> questions = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  public static Passage draft(String title, String content, CefrLevel level, User admin, Instant now) {
    Passage passage = new Passage();
    passage.status = PassageStatus.DRAFT;
    passage.createdBy = admin;
    passage.createdAt = now;
    passage.apply(title, content, level, now);
    return passage;
  }

  /** Sửa tiêu đề / nội dung / trình độ. Câu hỏi đang có phải còn khớp với nội dung mới. */
  public void update(String title, String content, CefrLevel level, Instant now) {
    requireEditable();
    apply(title, content, level, now);
    questions.forEach(q -> q.checkAgainst(this.content));
  }

  /**
   * Xóa hết câu hỏi để thay bằng bộ mới.
   *
   * Service PHẢI gọi passageRepository.flush() ngay sau method này, trước addQuestion:
   * lúc flush Hibernate chạy INSERT trước DELETE, nên câu mới position 0 sẽ đụng
   * UNIQUE (passage_id, position) của câu cũ chưa kịp xóa.
   */
  public void clearQuestions(Instant now) {
    requireEditable();
    questions.clear();
    updatedAt = now;
  }

  /** Câu hỏi phải được tạo cho chính bài này và thêm đúng thứ tự (position = số câu đang có). */
  public void addQuestion(ReadingQuestion question, Instant now) {
    requireEditable();
    if (question.getPassage() != this) {
      throw new IllegalArgumentException("Câu hỏi không thuộc bài đọc này");
    }
    if (question.getPosition() != questions.size()) {
      throw new IllegalArgumentException("Câu hỏi phải được thêm theo đúng thứ tự");
    }
    if (questions.size() >= MAX_QUESTIONS) {
      throw new IllegalArgumentException("Mỗi bài đọc tối đa " + MAX_QUESTIONS + " câu hỏi");
    }
    question.checkAgainst(content);
    questions.add(question);
    updatedAt = now;
  }

  public void publish(Instant now) {
    if (status != PassageStatus.DRAFT) {
      throw new IllegalStateException("Chỉ bài nháp mới publish được");
    }
    requireQuestions();
    status = PassageStatus.PUBLISHED;
    publishedAt = now;
    updatedAt = now;
  }

  /** Ẩn bài khỏi danh sách nhưng giữ lịch sử làm bài. Không có chiều ngược lại. */
  public void archive(Instant now) {
    if (status != PassageStatus.PUBLISHED) {
      throw new IllegalStateException("Chỉ bài đang publish mới lưu trữ được");
    }
    status = PassageStatus.ARCHIVED;
    updatedAt = now;
  }

  /** Bài đang publish mà bị sửa thành 0 câu hỏi thì học viên mở ra sẽ không có gì để làm. */
  public void requireQuestions() {
    if (questions.isEmpty()) {
      throw new IllegalArgumentException("Bài đọc phải có ít nhất 1 câu hỏi");
    }
  }

  public boolean isPublished() {
    return status == PassageStatus.PUBLISHED;
  }

  public int readingMinutes() {
    return Math.max(1, Math.round((float) wordCount / WORDS_PER_MINUTE));
  }

  public List<ReadingQuestion> getQuestions() {
    return Collections.unmodifiableList(questions);
  }

  /** Đếm từ: token có ít nhất một chữ cái hoặc chữ số (bỏ qua "—", "..."). */
  static int countWords(String text) {
    int count = 0;
    for (String token : text.strip().split("\\s+")) {
      if (token.codePoints().anyMatch(Character::isLetterOrDigit)) {
        count++;
      }
    }
    return count;
  }

  private void requireEditable() {
    if (status == PassageStatus.ARCHIVED) {
      throw new IllegalStateException("Bài đã lưu trữ, không sửa được");
    }
  }

  private void apply(String title, String content, CefrLevel level, Instant now) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Tiêu đề không được để trống");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Nội dung bài đọc không được để trống");
    }
    String cleanContent = content.strip();
    if (cleanContent.length() > MAX_CONTENT) {
      throw new IllegalArgumentException("Bài đọc tối đa " + MAX_CONTENT + " ký tự");
    }
    if (level == null) {
      throw new IllegalArgumentException("Vui lòng chọn trình độ");
    }
    this.title = title.strip();
    this.content = cleanContent;
    this.level = level;
    this.wordCount = countWords(cleanContent);
    this.updatedAt = now;
  }
}

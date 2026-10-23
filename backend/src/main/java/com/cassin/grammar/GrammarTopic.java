package com.cassin.grammar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.cassin.quiz.QuizAttempt;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "grammar_topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrammarTopic {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Inverse side (Phía không sở hữu): khóa ngoại topic_id nằm ở bảng questions.
  // [SỬA] Bỏ cascade = ALL và orphanRemoval. Topic → Question giống Deck → Card, KHÔNG giống
  // Question → QuestionOption: câu hỏi được thêm/sửa/xóa riêng từng cái, số lượng có thể rất lớn.
  // Xóa chủ đề thì DB tự xóa câu hỏi nhờ ON DELETE CASCADE (V6).
  // [SỬA] Khởi tạo list rỗng, tránh NullPointerException.
  // Muốn lấy câu hỏi của chủ đề (ví dụ chọn ngẫu nhiên 10 câu) thì dùng QuestionRepository.
  @OneToMany(mappedBy = "topic")
  private List<Question> questions = new ArrayList<>();

  // [SỬA] @OneToOne → @OneToMany: một chủ đề được làm quiz NHIỀU lần (bởi nhiều người).
  // Không cascade: DB chặn xóa chủ đề đã có người làm (V7).
  @OneToMany(mappedBy = "topic")
  private List<QuizAttempt> quizAttempts = new ArrayList<>();

  @Column(nullable = false, length = 150)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CefrLevel level;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String explanation;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public static GrammarTopic create(String title, CefrLevel level, String explanation) {
    GrammarTopic grammarTopic = new GrammarTopic();
    grammarTopic.apply(title, level, explanation);
    return grammarTopic;
  }

  public void update(String title, CefrLevel level, String explanation) {
    this.apply(title, level, explanation);
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  private void apply(String title, CefrLevel level, String explanation) {
    this.title = requireText(title, "Tiêu đề");
    this.level = Objects.requireNonNull(level, "Cấp độ CEFR không được null");
    this.explanation = requireText(explanation, "Giải thích");
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " không được để trống");
    }
    return value.strip();
  }
}


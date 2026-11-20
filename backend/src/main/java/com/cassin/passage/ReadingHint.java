package com.cassin.passage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Gợi ý bằng cặp diễn đạt lại: câu hỏi dùng "replacement", bài dùng "alternative". */
@Entity
@Table(name = "reading_hints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingHint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private ReadingQuestion question;

  @Column(nullable = false)
  private int position;

  @Column(name = "question_phrase", nullable = false, length = 100)
  private String questionPhrase;

  @Column(name = "passage_phrase", nullable = false, length = 100)
  private String passagePhrase;

  /** Package-private: chỉ ReadingQuestion tạo gợi ý (sau khi đã kiểm tra hợp lệ). */
  ReadingHint(ReadingQuestion question, int position, String questionPhrase, String passagePhrase) {
    this.question = question;
    this.position = position;
    this.questionPhrase = questionPhrase;
    this.passagePhrase = passagePhrase;
  }
}

package com.cassin.passage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lựa chọn A..D của câu MCQ. */
@Entity
@Table(name = "reading_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingOption {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Trỏ thẳng tới lớp con McqQuestion: chỉ câu MCQ mới có lựa chọn.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private McqQuestion question;

  @Column(nullable = false, length = 255)
  private String content;

  // Cột is_correct; Lombok sinh isCorrect() cho field boolean tên correct.
  @Column(name = "is_correct", nullable = false)
  private boolean correct;

  @Column(nullable = false)
  private int position;

  ReadingOption(McqQuestion question, int position, String content, boolean correct) {
    this.question = question;
    this.position = position;
    this.content = content;
    this.correct = correct;
  }

  /** A, B, C, D */
  public char label() {
    return (char) ('A' + position);
  }
}

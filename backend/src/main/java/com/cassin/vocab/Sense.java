package com.cassin.vocab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.cassin.report.ContentReport;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MỘT nghĩa của một từ: loại từ + nghĩa + câu ví dụ. Đây là đơn vị được ÔN (Leitner theo từng nghĩa).
 *
 * Thay cho Card cũ: bảng cards được V10 đổi tên thành senses, GIỮ NGUYÊN id,
 * nên card_progresses (tiến độ) và lịch sử không bị mất.
 *
 *   light ─┬─ (n)   ánh sáng   "Turn on the [light]."
 *          └─ (adj) nhẹ        "This bag is [light] enough to carry."
 *
 * Mọi thay đổi đi qua Word (aggregate root, giống Question → QuestionOption): constructor và
 * change(...) là package-private, bên ngoài package vocab không tạo / sửa Sense lẻ được.
 */
@Entity
@Table(name = "senses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sense {
  public static final int MAX_EXAMPLES = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Owning side: bảng senses giữ khóa ngoại word_id.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "word_id", nullable = false)
  private Word word;

  @OneToMany(mappedBy = "sense")
  private List<ContentReport> contentReports = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "part_of_speech", nullable = false, length = 20)
  private PartOfSpeech partOfSpeech;

  @Column(nullable = false, length = 255)
  private String definition;

  // Thứ tự hiển thị trong từ (0, 1, 2...). Word.replaceSenses gán lại mỗi lần sửa.
  @Column(nullable = false)
  private int position;

  // Câu ví dụ: lưu NGUYÊN VĂN kể cả dấu [ ] đánh dấu từ đích (xem ExampleMarkup).
  @ElementCollection
  @CollectionTable(name = "sense_examples", joinColumns = @JoinColumn(name = "sense_id"))
  @OrderColumn(name = "position")
  @Column(name = "example", nullable = false, length = 500)
  private List<String> examples = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Chỉ Word.replaceSenses gọi. */
  Sense(Word word) {
    this.word = Objects.requireNonNull(word, "Nghĩa phải thuộc một từ");
  }

  /** Chỉ Word.replaceSenses gọi. Dữ liệu từng field được kiểm tra ở đây. */
  void change(PartOfSpeech partOfSpeech, String definition, List<String> examples, int position) {
    this.partOfSpeech = Objects.requireNonNull(partOfSpeech, "Loại từ là bắt buộc");
    this.definition = requireText(definition, "Nghĩa");
    this.position = position;
    List<String> cleaned = cleanExamples(examples);
    this.examples.clear();
    this.examples.addAll(cleaned);
    this.updatedAt = Instant.now();
  }

  /** Câu ví dụ để hiển thị (bỏ dấu [ ]). */
  public List<String> plainExamples() {
    List<String> plain = new ArrayList<>();
    for (String example : examples) {
      plain.add(ExampleMarkup.plainOf(example));
    }
    return plain;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " không được để trống");
    }
    return value.strip();
  }

  // Bỏ câu trống, cắt khoảng trắng, kiểm tra định dạng đánh dấu, tối đa MAX_EXAMPLES câu.
  // Package-private: Word.replaceSenses gọi trước ở vòng kiểm tra, để lỗi nổ TRƯỚC khi sửa bất cứ nghĩa nào.
  static List<String> cleanExamples(List<String> examples) {
    List<String> cleaned = new ArrayList<>();
    if (examples == null) {
      return cleaned;
    }
    for (String example : examples) {
      if (example == null || example.isBlank()) {
        continue;
      }
      String value = example.strip();
      ExampleMarkup.validate(value);
      cleaned.add(value);
    }
    if (cleaned.size() > MAX_EXAMPLES) {
      throw new IllegalArgumentException("Mỗi nghĩa tối đa " + MAX_EXAMPLES + " câu ví dụ");
    }
    return cleaned;
  }
}

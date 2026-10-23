package com.cassin.vocab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Thẻ từ vựng. Mỗi thẻ là MỘT nghĩa của MỘT từ (một loại từ + một nghĩa).
 * Từ nhiều nghĩa / nhiều loại từ = nhiều thẻ cùng "word" trong một bộ.
 *
 * Tiến độ ôn (hộp Leitner, hạn ôn) KHÔNG nằm ở đây, vì một thẻ của bộ hệ thống
 * được nhiều người ôn với tiến độ khác nhau → sẽ nằm ở entity CardProgress.
 */
@Entity
@Table(name = "cards")
@Getter
// JPA bắt buộc phải có constructor không tham số để tạo object khi đọc từ DB.
// Để PROTECTED: JPA vẫn dùng được, còn code bên ngoài package không gọi new Card() được.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

  /** Số câu ví dụ tối đa cho một thẻ. */
  public static final int MAX_EXAMPLES = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String word;

  @Enumerated(EnumType.STRING)
  @Column(name = "part_of_speech", nullable = false, length = 20)
  private PartOfSpeech partOfSpeech;

  @Column(length = 100)
  private String phonetic;

  @Column(nullable = false, length = 255)
  private String definition;

  // Mỗi câu ví dụ là một dòng trong bảng card_examples, trỏ về thẻ qua card_id.
  // Không phải entity: không có id riêng, xóa thẻ là xóa luôn ví dụ.
  // Mặc định LAZY: chỉ truy vấn bảng card_examples khi gọi getExamples().
  @ElementCollection
  @CollectionTable(name = "card_examples", joinColumns = @JoinColumn(name = "card_id"))
  @OrderColumn(name = "position") // giữ đúng thứ tự người dùng nhập
  @Column(name = "example", nullable = false, length = 500)
  private List<String> examples = new ArrayList<>();

  // Owning side: bảng cards giữ khóa ngoại deck_id.
  // optional = false + nullable = false: thẻ LUÔN phải thuộc một bộ.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "deck_id", nullable = false)
  private Deck deck;

  // Inverse side: mỗi card có N CardProgress của N user
  @OneToMany(mappedBy = "card")
  private List<CardProgress> progresses = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  // [SỬA] Đổi tên tham số parent → deck cho đúng nghĩa, và chặn deck null (fail fast).
  public static Card create(
    String word,
    PartOfSpeech partOfSpeech,
    String phonetic,
    String definition,
    List<String> examples,
    Deck deck
  ) {
    Objects.requireNonNull(deck, "Thẻ phải thuộc một bộ");

    Card card = new Card();
    card.deck = deck;
    card.apply(word, partOfSpeech, phonetic, definition, examples);
    return card;
  }

  // [SỬA] Bỏ tham số "Card oldCard": đây là method của chính thẻ, nên gán vào this.
  // Gọi ở service: card.update(...). Không có tham số deck → không ai chuyển thẻ sang bộ khác được.
  // Không cần gọi repository.save(): thẻ đọc ra trong @Transactional được Hibernate theo dõi,
  // cuối transaction nó tự phát hiện field thay đổi và chạy UPDATE ("dirty checking").
  public void update(
    String word,
    PartOfSpeech partOfSpeech,
    String phonetic,
    String definition,
    List<String> examples
  ) {
    apply(word, partOfSpeech, phonetic, definition, examples);
  }

  // JPA tự gọi method này ngay trước khi chạy câu UPDATE.
  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // [SỬA] create và update dùng chung một chỗ chuẩn hóa + kiểm tra, không viết lặp.
  private void apply(
    String word,
    PartOfSpeech partOfSpeech,
    String phonetic,
    String definition,
    List<String> examples
  ) {
    this.word = requireText(word, "Từ");
    this.partOfSpeech = Objects.requireNonNull(partOfSpeech, "Loại từ là bắt buộc");
    this.phonetic = blankToNull(phonetic);
    this.definition = requireText(definition, "Nghĩa");

    // [SỬA] Không gán thẳng list nhận vào (card.examples = examples):
    // - list bên ngoài bị sửa thì thẻ bị sửa theo;
    // - Hibernate đang theo dõi chính object list này, nên chỉ clear() rồi addAll().
    this.examples.clear();
    this.examples.addAll(cleanExamples(examples));
  }

  // [SỬA] Trước đây normalizeWord() gọi toLowerCase() → "I", "London", "Monday" bị lưu sai,
  // phiên âm và nghĩa tiếng Việt cũng bị đổi chữ. Chỉ cắt khoảng trắng thôi.
  // Việc so sánh không phân biệt hoa thường để DB làm (unique index trên lower(word), lower(definition)).
  // Và phonetic = null trước đây sẽ nổ NullPointerException.
  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " không được để trống");
    }
    return value.strip();
  }

  private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value.strip();
  }

  // Bỏ phần tử null và câu rỗng, cắt khoảng trắng, giới hạn số câu.
  // Dùng vòng for thay vì stream: trình phân tích null của IDE theo dõi được câu if bên dưới
  // ("example" chắc chắn khác null khi đến dòng strip()), nhưng không theo được qua
  // filter(Objects::nonNull) → map(String::strip) trong stream, nên báo warning "Null type safety".
  private static List<String> cleanExamples(List<String> examples) {
    List<String> cleaned = new ArrayList<>();
    if (examples == null) {
      return cleaned;
    }
    for (String example : examples) {
      if (example == null || example.isBlank()) {
        continue;
      }
      cleaned.add(example.strip());
    }
    if (cleaned.size() > MAX_EXAMPLES) {
      throw new IllegalArgumentException("Tối đa " + MAX_EXAMPLES + " câu ví dụ");
    }
    return cleaned;
  }
}

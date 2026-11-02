package com.cassin.vocab;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.cassin.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bộ từ vựng = NHÃN CHỦ ĐỀ gom các từ lại (từ V10, từ không còn "thuộc về" bộ). Có 2 loại:
 * - SYSTEM_DECK: bộ hệ thống, không có chủ (owner = null), mọi người dùng đều ôn được.
 * - USER_DECK: bộ cá nhân, luôn có đúng một chủ.
 *
 * Chỉ tạo Deck qua 2 factory method personal(...) và system(...).
 * Nhờ vậy không thể tạo ra trạng thái vô lý như "bộ cá nhân không có chủ"
 * hay "bộ hệ thống lại có chủ".
 */
@Entity
@Table(name = "decks")
@Getter
// JPA bắt buộc phải có constructor không tham số để tạo object khi đọc từ DB.
// Để PROTECTED: JPA vẫn dùng được, còn code bên ngoài package không gọi new Deck() được.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deck {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "deck_name", nullable = false, length = 100)
  private String deckName;

  @Column(length = 500)
  private String description;

  // Loại bộ từ: hệ thống hay cá nhân. Giá trị được gán trong factory method, không đặt mặc định.
  @Enumerated(EnumType.STRING)
  @Column(name = "deck_type", nullable = false, length = 20)
  private DeckType deckType;

  // Owning side (Phía sở hữu): bảng decks giữ khóa ngoại owner_id trỏ sang users.
  // LAZY: chỉ truy vấn bảng users khi thật sự gọi getOwner().
  // Có thể null: bộ hệ thống không có chủ.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  private User owner;

  // [V10] Bộ ↔ từ là NHIỀU-NHIỀU qua bảng deck_words (trước đây: bộ 1-nhiều thẻ).
  // Một từ nằm được trong nhiều bộ; xóa bộ chỉ xóa dòng trong deck_words, từ vẫn còn trong sổ từ.
  //
  // Đây là owning side (khai báo @JoinTable). Nhưng KHÔNG thêm / bỏ từ bằng cách sửa Set này:
  // getWords().add(...) buộc Hibernate tải TOÀN BỘ từ của bộ lên trước. Dùng
  // DeckRepository.addWord / removeWord (1 câu SQL). Set này chỉ để viết JPQL "join d.words".
  // Cột added_at của deck_words không map ở đây: DB tự điền DEFAULT now().
  @ManyToMany
  @JoinTable(
      name = "deck_words",
      joinColumns = @JoinColumn(name = "deck_id"),
      inverseJoinColumns = @JoinColumn(name = "word_id"))
  private Set<Word> words = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /**
   * Tạo bộ cá nhân cho một người dùng.
   *
   * owner == null nghĩa là code gọi đến đang có bug (quên lấy user từ token, tra user không thấy...).
   * Nguyên tắc "fail fast": ném lỗi ngay để bug lộ ra, thay vì lặng lẽ tạo ra một bộ hệ thống
   * công khai mà không ai biết.
   */
  public static Deck personal(String deckName, String description, User owner) {
    Objects.requireNonNull(owner, "Bộ cá nhân phải có chủ");

    Deck deck = new Deck();
    deck.apply(deckName, description);
    deck.owner = owner;
    deck.deckType = DeckType.USER_DECK;
    return deck;
  }

  /**
   * Tạo bộ hệ thống (chỉ ADMIN được gọi; việc kiểm tra quyền nằm ở service).
   * Bộ hệ thống không có chủ nên không nhận tham số owner.
   */
  public static Deck system(String deckName, String description) {
    Deck deck = new Deck();
    deck.apply(deckName, description);
    deck.deckType = DeckType.SYSTEM_DECK;
    return deck;
  }

  // [THÊM] Sửa tên và mô tả bộ (API PUT /decks/{id} sẽ cần).
  // Không cho đổi owner hay deckType: đó là nghiệp vụ khác, không phải "sửa bộ".
  public void update(String deckName, String description) {
    apply(deckName, description);
    // [SỬA] Gán updatedAt NGAY khi sửa, không chỉ chờ @PreUpdate.
    // @PreUpdate chỉ chạy lúc Hibernate flush (cuối transaction), tức là SAU KHI
    // DeckService.updateDeck đã dựng xong DeckResponse → response PUT /api/decks/{id}
    // trả về updatedAt CŨ, frontend hiện "Cập nhật 3 ngày trước" ngay sau khi vừa sửa.
    // @PreUpdate vẫn giữ: mọi thay đổi khác của bộ (nếu sau này có) vẫn được cập nhật thời gian.
    this.updatedAt = Instant.now();
  }

  /**
   * Bộ này có phải của người dùng userId không? Dùng ở service để kiểm tra quyền sửa/xóa.
   *
   * - Bộ hệ thống: luôn false (không ai "sở hữu" bộ hệ thống, kể cả ADMIN;
   *   quyền của ADMIN được kiểm tra riêng ở service).
   * - Kiểm tra owner trước rồi mới gọi owner.getId(), tránh NullPointerException.
   * - Lưu ý: gọi owner.getId() trên quan hệ LAZY không làm Hibernate truy vấn thêm,
   *   vì id đã có sẵn trong cột owner_id của chính bảng decks.
   */
  public boolean isOwnedBy(Long userId) {
    if (deckType == DeckType.SYSTEM_DECK || owner == null) {
      return false;
    }
    // Dùng Objects.equals thay vì ==: Long là object, so sánh bằng == chỉ đúng với số nhỏ (-128..127).
    return Objects.equals(owner.getId(), userId);
  }

  /** [V10] Có thay đổi danh sách từ (thêm / bỏ từ): cập nhật thời gian để danh sách bộ sắp xếp đúng. */
  public void touch() {
    this.updatedAt = Instant.now();
  }

  /**
   * [V10] Bộ này nhận được từ này không?
   *   - Bộ hệ thống: chỉ chứa từ hệ thống (học viên không thể "nhét" từ riêng vào bộ chung).
   *   - Bộ cá nhân: chứa từ của chính chủ bộ, hoặc từ hệ thống (vd. gom từ hệ thống vào bộ "IELTS" của mình).
   */
  public boolean canContain(Word word) {
    if (deckType == DeckType.SYSTEM_DECK) {
      return word.isSystem();
    }
    return word.isSystem() || word.isOwnedBy(owner.getId());
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // [SỬA] Trước đây gán thẳng: tên "   " (toàn dấu cách) vẫn lưu được, mô tả rỗng lưu thành "".
  private void apply(String deckName, String description) {
    if (deckName == null || deckName.isBlank()) {
      throw new IllegalArgumentException("Tên bộ không được để trống");
    }
    this.deckName = deckName.strip();
    this.description = (description == null || description.isBlank()) ? null : description.strip();
  }
}

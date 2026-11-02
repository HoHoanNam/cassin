package com.cassin.vocab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cassin.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MỘT từ trong sổ từ, gồm nhiều nghĩa (Sense).
 *
 *   owner != null : từ của học viên, chỉ chủ sửa được
 *   owner == null : từ HỆ THỐNG (nằm trong bộ hệ thống), chỉ admin sửa được, ai cũng học được
 *
 * Từ KHÔNG thuộc về bộ nào: bộ (Deck) chỉ là nhãn chủ đề, quan hệ nhiều-nhiều qua bảng deck_words.
 * Xóa một bộ cá nhân thì các từ trong bộ vẫn còn trong sổ từ.
 *
 * Word là aggregate root của nhóm Word + Sense: thêm / sửa / xóa nghĩa đều đi qua replaceSenses,
 * để luật "ít nhất 1 nghĩa, không trùng nghĩa" luôn được kiểm tra.
 */
@Entity
@Table(name = "words")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Word {
  public static final int MAX_SENSES = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // NULL = từ hệ thống. Gọi owner.getId() trên quan hệ LAZY không làm Hibernate truy vấn thêm.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  private User owner;

  @Column(nullable = false, length = 100)
  private String term;

  @Column(length = 100)
  private String phonetic;

  // cascade ALL + orphanRemoval: nghĩa không tồn tại độc lập; bỏ khỏi list = xóa khỏi DB.
  // @OrderBy: Hibernate luôn trả nghĩa theo đúng thứ tự position.
  @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<Sense> senses = new ArrayList<>();

  // Inverse side của Deck.words: chỉ để ĐỌC (vd. truy vấn JPQL). Thêm / bỏ từ khỏi bộ
  // dùng DeckRepository.addWord / removeWord (câu SQL trực tiếp, không tải cả bộ lên).
  @ManyToMany(mappedBy = "words")
  private Set<Deck> decks = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Từ của một học viên. */
  public static Word personal(String term, String phonetic, User owner) {
    Word word = new Word();
    word.owner = Objects.requireNonNull(owner, "Từ cá nhân phải có chủ");
    word.apply(term, phonetic);
    return word;
  }

  /** Từ hệ thống (admin tạo). */
  public static Word system(String term, String phonetic) {
    Word word = new Word();
    word.apply(term, phonetic);
    return word;
  }

  public void update(String term, String phonetic) {
    apply(term, phonetic);
    this.updatedAt = Instant.now();
  }

  public boolean isSystem() {
    return owner == null;
  }

  public boolean isOwnedBy(Long userId) {
    return owner != null && Objects.equals(owner.getId(), userId);
  }

  /** Học viên XEM được từ này không: từ của mình, hoặc từ hệ thống. */
  public boolean isReadableBy(Long userId) {
    return isSystem() || isOwnedBy(userId);
  }

  /**
   * Thay toàn bộ danh sách nghĩa, GIỮ NGUYÊN id những nghĩa vẫn còn (tiến độ ôn gắn với id):
   *
   *   input có id    → sửa nghĩa đó tại chỗ
   *   input không id → thêm nghĩa mới
   *   nghĩa cũ không có trong input → bị xóa (orphanRemoval, kéo theo tiến độ của nghĩa đó)
   *
   * Thứ tự trong input = thứ tự hiển thị (position 0, 1, 2...).
   * Kiểm tra HẾT rồi mới đổi: input sai thì từ giữ nguyên trạng thái cũ.
   */
  public void replaceSenses(List<SenseInput> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      throw new IllegalArgumentException("Từ phải có ít nhất 1 nghĩa");
    }
    if (inputs.size() > MAX_SENSES) {
      throw new IllegalArgumentException("Mỗi từ tối đa " + MAX_SENSES + " nghĩa");
    }

    // Vòng 1: kiểm tra (không đổi gì)
    Map<Long, Sense> byId = new HashMap<>();
    for (Sense sense : senses) {
      byId.put(sense.getId(), sense);
    }
    Set<String> seenDefinitions = new HashSet<>();
    Set<Long> seenIds = new HashSet<>();
    for (SenseInput input : inputs) {
      if (input == null) {
        throw new IllegalArgumentException("Nghĩa không được null");
      }
      if (input.id() != null && (!byId.containsKey(input.id()) || !seenIds.add(input.id()))) {
        throw new IllegalArgumentException("Nghĩa " + input.id() + " không thuộc từ này");
      }
      if (input.partOfSpeech() == null) {
        throw new IllegalArgumentException("Loại từ là bắt buộc");
      }
      if (input.definition() == null || input.definition().isBlank()) {
        throw new IllegalArgumentException("Nghĩa không được để trống");
      }
      if (!seenDefinitions.add(input.definition().strip().toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("Hai nghĩa không được trùng nhau: " + input.definition().strip());
      }
      Sense.cleanExamples(input.examples());   // câu ví dụ sai định dạng / quá 5 câu → lỗi ngay ở vòng này
    }

    // Vòng 2: áp dụng. Mọi lỗi đã bị bắt ở vòng 1 nên vòng này không còn ném lỗi giữa chừng
    // (tránh tình trạng nghĩa thứ nhất đã bị sửa, nghĩa thứ hai mới báo lỗi).
    List<Sense> next = new ArrayList<>();
    for (int position = 0; position < inputs.size(); position++) {
      SenseInput input = inputs.get(position);
      Sense sense = input.id() == null ? new Sense(this) : byId.get(input.id());
      sense.change(input.partOfSpeech(), input.definition(), input.examples(), position);
      next.add(sense);
    }
    senses.clear();
    senses.addAll(next);
    this.updatedAt = Instant.now();
  }

  /** Danh sách CHỈ ĐỌC: không ai thêm / xóa nghĩa mà bỏ qua replaceSenses được. */
  public List<Sense> getSenses() {
    return Collections.unmodifiableList(senses);
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  private void apply(String term, String phonetic) {
    if (term == null || term.isBlank()) {
      throw new IllegalArgumentException("Từ không được để trống");
    }
    this.term = term.strip();
    this.phonetic = (phonetic == null || phonetic.isBlank()) ? null : phonetic.strip();
  }
}

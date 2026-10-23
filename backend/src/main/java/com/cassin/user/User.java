package com.cassin.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.cassin.quiz.QuizAttempt;
import com.cassin.vocab.CardProgress;
import com.cassin.vocab.Deck;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
  // Database tự tăng ID
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  // [SỬA] Thêm length = 100 cho khớp VARCHAR(100) trong V1 (mặc định của JPA là 255).
  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(length = 500)
  private String bio;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role = Role.USER;

  // Inverse side (Phía được ánh xạ): mappedBy = tên field ở phía @ManyToOne (Deck.owner).
  // Chỉ để ĐỌC, khóa ngoại nằm ở bảng decks.
  // [SỬA] Bỏ cascade = ALL và orphanRemoval:
  // - Xóa user thì DB tự xóa bộ cá nhân nhờ ON DELETE CASCADE (V2), không cần Hibernate làm.
  // - Comment cũ về orphanRemoval bị nhầm: orphanRemoval = "bỏ 1 deck khỏi list decks thì XÓA
  //   chính deck đó khỏi DB", không phải xóa thẻ bên trong. Rất dễ vô tình xóa mất dữ liệu.
  // [SỬA] Khởi tạo list rỗng, tránh NullPointerException.
  // Muốn lấy bộ của user thì nên dùng DeckRepository (có phân trang).
  @OneToMany(mappedBy = "owner")
  private List<Deck> decks = new ArrayList<>();

  // Inverse side: mỗi user có N CardProgress của N card
  @OneToMany(mappedBy = "user")
  private List<CardProgress> progresses = new ArrayList<>();

  // [SỬA] @OneToOne → @OneToMany: một người làm NHIỀU lượt quiz, không phải đúng một lượt.
  // Chỉ để đọc, không cascade. Lấy lịch sử làm bài thì dùng QuizAttemptRepository (có phân trang).
  @OneToMany(mappedBy = "user")
  private List<QuizAttempt> quizAttempts = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public User(String email, String passwordHash, String displayName) {
    // [SỬA] Chuẩn hóa email: "  An@Gmail.com " và "an@gmail.com" là một người.
    // Không chuẩn hóa thì cùng một email đăng ký được 2 lần, vì UNIQUE trong DB phân biệt hoa thường.
    // Locale.ROOT: tránh lỗi đổi chữ theo ngôn ngữ máy (ví dụ máy cài tiếng Thổ Nhĩ Kỳ, "I" → "ı").
    this.email = requireText(email, "Email").toLowerCase(Locale.ROOT);
    this.passwordHash = requireText(passwordHash, "Mật khẩu");
    this.displayName = requireText(displayName, "Tên hiển thị");
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
}

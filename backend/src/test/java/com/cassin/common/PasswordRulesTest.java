package com.cassin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * [THÊM] Test cho PasswordRules: giới hạn 72 BYTE của BCrypt, không phải 72 ký tự.
 */
class PasswordRulesTest {

  @Test
  @DisplayName("Chữ không dấu: 1 ký tự = 1 byte")
  void ascii_oneBytePerChar() {
    assertThat(PasswordRules.byteLength("matkhau")).isEqualTo(7);
  }

  @Test
  @DisplayName("Chữ có dấu: cùng 7 ký tự nhưng nhiều byte hơn")
  void vietnamese_moreBytesThanChars() {
    assertThat("mậtkhẩu").hasSize(7);
    assertThat(PasswordRules.byteLength("mậtkhẩu")).isEqualTo(11);
  }

  @Test
  @DisplayName("Đúng 72 byte: hợp lệ")
  void exactly72Bytes_isAccepted() {
    assertThatCode(() -> PasswordRules.requireBcryptLength("a".repeat(72))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("40 ký tự có dấu (qua được @Size(max = 72)) nhưng 120 byte → 400 PASSWORD_TOO_LONG")
  void under72Chars_butOver72Bytes_isRejected() {
    String password = "ậ".repeat(40); // 40 ký tự, mỗi ký tự 3 byte
    assertThat(password).hasSizeLessThanOrEqualTo(72);

    assertThatThrownBy(() -> PasswordRules.requireBcryptLength(password))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo("PASSWORD_TOO_LONG");
        });
  }

  @Test
  @DisplayName("Đúng là BCrypt KHÔNG nhận chuỗi quá 72 byte: lý do PasswordRules tồn tại")
  void bcryptItselfRejectsLongPasswords() {
    // Nếu một bản Spring Security sau này đổi hành vi (không ném nữa), test này báo cho mình biết.
    assertThatThrownBy(() -> new BCryptPasswordEncoder().encode("ậ".repeat(40)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

package com.cassin.common;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;

/**
 * [THÊM] Luật mật khẩu dùng chung cho đăng ký, đổi mật khẩu và AdminBootstrap.
 *
 * ── Vì sao cần, khi DTO đã có @Size(max = 72)? ──
 * @Size đếm KÝ TỰ, còn BCrypt giới hạn 72 BYTE. Tiếng Việt có dấu tốn 2–3 byte mỗi ký tự (UTF-8):
 *
 *   "matkhau"   → 7 ký tự = 7 byte
 *   "mậtkhẩu"   → 7 ký tự = 11 byte  ("ậ", "ẩ" mỗi chữ 3 byte)
 *
 * Mật khẩu 40 ký tự toàn chữ có dấu = khoảng 100 byte: qua được @Size(max = 72), nhưng
 * BCryptPasswordEncoder (Spring Security bản mới, sau bản vá CVE-2025-22228) NÉM IllegalArgumentException
 * khi encode() chuỗi dài hơn 72 byte → người dùng nhận lỗi 500 thay vì một câu báo dễ hiểu.
 * (Bản cũ không ném mà lặng lẽ CẮT bỏ phần sau byte thứ 72: hai mật khẩu khác nhau ở cuối bị coi là một.)
 *
 * Kiểm tra theo byte TRƯỚC khi băm → 400 PASSWORD_TOO_LONG kèm câu hướng dẫn.
 */
public final class PasswordRules {
  /** Giới hạn của thuật toán BCrypt, không đổi được. */
  public static final int MAX_BYTES = 72;

  private PasswordRules() {
  }

  /** Số byte của mật khẩu khi mã hóa UTF-8 (đúng cách BCryptPasswordEncoder đọc chuỗi). */
  public static int byteLength(String rawPassword) {
    return rawPassword == null ? 0 : rawPassword.getBytes(StandardCharsets.UTF_8).length;
  }

  /** Gọi ngay trước passwordEncoder.encode(...). Quá 72 byte → 400 PASSWORD_TOO_LONG. */
  public static void requireBcryptLength(String rawPassword) {
    if (byteLength(rawPassword) > MAX_BYTES) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG",
          "Mật khẩu quá dài. Chữ có dấu tốn nhiều chỗ hơn chữ không dấu, hãy rút ngắn hoặc bớt dấu.");
    }
  }
}

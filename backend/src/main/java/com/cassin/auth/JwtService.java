package com.cassin.auth;

import java.time.Duration;
import java.time.Instant;
// [SỬA] Trước đây import java.sql.Date: đó là kiểu NGÀY của JDBC (không có giờ phút).
// JJWT cần java.util.Date (một thời điểm chính xác tới mili giây).
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.cassin.user.Role;
import com.cassin.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Tạo và đọc JWT. Đây là nơi DUY NHẤT trong app biết khóa bí mật.
 *
 * Một token có 3 phần, nối bằng dấu chấm: header.payload.signature
 * - payload: id, email, role, hạn dùng. Chỉ mã hóa base64, AI CŨNG ĐỌC ĐƯỢC → không bao giờ để mật khẩu.
 * - signature: tạo từ payload + khóa bí mật. Sửa 1 ký tự trong payload là chữ ký sai ngay.
 * Nhờ chữ ký, server tin token mà không cần lưu gì trong database (stateless).
 */
@Service
public class JwtService {
  private final SecretKey key;
  private final Duration ttl;

  public JwtService(JwtProperties properties) {
    // Đổi chuỗi base64 trong cấu hình thành khóa cho thuật toán HMAC-SHA.
    // Khóa phải dài tối thiểu 32 byte (256 bit), ngắn hơn thì JJWT ném lỗi ngay lúc khởi động.
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    this.ttl = properties.accessTokenTtl();
  }

  /** Tạo token sau khi đăng nhập thành công. */
  public String generate(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())          // "sub": token này của user nào
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())      // lưu dạng chữ "USER" / "ADMIN"
        .issuedAt(Date.from(now))                  // "iat": thời điểm cấp
        .expiration(Date.from(now.plus(ttl)))      // "exp": hết hạn sau ttl (1 giờ)
        .signWith(key)                             // ký bằng khóa bí mật
        .compact();                                // ghép thành chuỗi "xxx.yyy.zzz"
  }

  /**
   * Đọc token và dựng lại người dùng.
   * Chữ ký sai, token bị sửa, hoặc đã hết hạn → JJWT ném JwtException
   * (JwtAuthenticationFilter bắt lỗi này và coi như chưa đăng nhập).
   */
  public AuthUser parse(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(key)            // kiểm tra chữ ký bằng đúng khóa đã dùng để ký
        .build()
        .parseSignedClaims(token)   // kiểm tra luôn hạn dùng "exp"
        .getPayload();
    return new AuthUser(
        Long.valueOf(claims.getSubject()),
        claims.get("email", String.class),
        // [SỬA] Trước đây import nhầm javax.management.relation.Role (một class của Java JMX,
        // không liên quan) → không có valueOf(String) → lỗi compile. Phải là com.cassin.user.Role.
        Role.valueOf(claims.get("role", String.class)));
  }
}

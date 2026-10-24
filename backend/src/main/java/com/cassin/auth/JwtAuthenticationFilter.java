package com.cassin.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * "Bác bảo vệ đọc thẻ": chạy MỘT LẦN cho mỗi request, TRƯỚC khi tới controller.
 *
 * Nhiệm vụ DUY NHẤT: có token hợp lệ thì ghi vào SecurityContext "request này là của user X, vai trò Y".
 * Filter KHÔNG tự chặn request nào. Việc chặn (401 / 403) là của luật trong SecurityConfig,
 * chạy sau filter này trong cùng SecurityFilterChain.
 *
 *   Request ──▶ JwtAuthenticationFilter ──▶ luật SecurityConfig ──▶ Controller
 *               (đọc token, ghi danh tính)   (cho qua / 401 / 403)
 *
 * KHÔNG gắn @Component: Spring Boot tự đăng ký mọi Filter là bean vào server,
 * khiến filter chạy thêm một lần nữa ngoài chuỗi bảo mật. Filter được tạo bằng new trong SecurityConfig.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    // Client gửi: Authorization: Bearer eyJhbGciOi...
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (header != null && header.startsWith(BEARER_PREFIX)) {
      String token = header.substring(BEARER_PREFIX.length());   // bỏ chữ "Bearer "
      try {
        AuthUser user = jwtService.parse(token);

        // Spring Security quy ước vai trò có tiền tố "ROLE_".
        // Nhờ vậy luật hasRole("ADMIN") trong SecurityConfig khớp với "ROLE_ADMIN" ở đây.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));

        // principal = AuthUser → controller lấy ra bằng @AuthenticationPrincipal AuthUser.
        // credentials = null: đã xác thực bằng token, không cần giữ mật khẩu.
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);

        // Ghi danh tính vào SecurityContext của request hiện tại.
        // Từ đây, Spring Security coi request này là "đã đăng nhập".
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (JwtException | IllegalArgumentException ex) {
        // Token sai chữ ký, bị sửa, hết hạn, hoặc role lạ: coi như chưa đăng nhập.
        // Không trả lỗi ở đây: nếu endpoint cần đăng nhập, SecurityConfig sẽ trả 401.
        SecurityContextHolder.clearContext();
      }
    }

    // LUÔN chuyển request đi tiếp, dù có token hay không.
    // Quên dòng này thì request dừng ở đây và client nhận response rỗng.
    chain.doFilter(request, response);
  }
}

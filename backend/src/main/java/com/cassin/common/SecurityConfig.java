package com.cassin.common;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.cassin.auth.JwtAuthenticationFilter;
import com.cassin.auth.JwtProperties;
import com.cassin.auth.JwtService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * "Bảng quy định tầng nào ai được lên": luật bảo mật cho toàn bộ API.
 *
 * SecurityFilterChain = chuỗi các "trạm kiểm soát" mọi request đi qua trước khi tới controller.
 * Spring Security có sẵn nhiều trạm; ở đây mình khai báo luật cho chuỗi đó
 * và chèn thêm trạm JwtAuthenticationFilter của mình vào.
 */
@Configuration
// Bật việc đọc cassin.jwt.* trong application.yml vào record JwtProperties.
// Thiếu dòng này thì Spring không tạo được JwtProperties → JwtService không khởi tạo được.
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtService jwtService;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        // CSRF chống tấn công dựa vào COOKIE đăng nhập. Cassin gửi token trong HEADER, không dùng cookie
        // → tắt được. (Nếu sau này chuyển token sang cookie thì phải bật lại.)
        .csrf(csrf -> csrf.disable())

        // Không tạo session trên server: mỗi request tự chứng minh danh tính bằng token.
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // LUẬT: xét từ trên xuống, luật đầu tiên khớp được áp dụng.
        .authorizeHttpRequests(auth -> auth
            // Ai cũng gọi được: đăng ký, đăng nhập.
            // "/error": khi có lỗi 500, Spring chuyển nội bộ sang /error. Khóa nó thì
            // bạn nhận 401 thay cho lỗi thật, rất khó tìm nguyên nhân.
            .requestMatchers("/api/auth/**", "/error").permitAll()
            // Chỉ ADMIN (authority "ROLE_ADMIN" do JwtAuthenticationFilter gắn vào).
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            // Mọi API còn lại: phải đăng nhập.
            .anyRequest().authenticated())

        .exceptionHandling(e -> e
            // CHƯA đăng nhập (không có token / token hỏng) mà gọi API cần đăng nhập → 401.
            .authenticationEntryPoint((request, response, ex) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Bạn cần đăng nhập"))
            // ĐÃ đăng nhập nhưng không đủ quyền (USER gọi /api/admin/**) → 403.
            .accessDeniedHandler((request, response, ex) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền thực hiện thao tác này")))

        // Chèn filter JWT TRƯỚC filter đăng nhập mặc định của Spring (form login),
        // để danh tính đã có sẵn khi các luật ở trên được kiểm tra.
        .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /**
   * Viết lỗi 401/403 thành JSON cùng dạng ErrorResponse.
   * Phải tự viết vì lỗi này xảy ra trong filter, TRƯỚC controller,
   * nên GlobalExceptionHandler (chỉ bắt lỗi từ controller) không xử lý được.
   */
  private static void writeError(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(
        "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fieldErrors\":{}}");
  }
}

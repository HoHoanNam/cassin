package com.cassin.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @Slf4j: dùng để tự động tạo ra một đối tượng Logger trong class mà bạn không cần phải viết code khởi tạo dài dòng.
 * VD: private static final Logger log = LoggerFactory.getLogger(MyService.class);
 */
@Slf4j 
@Component 
@RequiredArgsConstructor
// Chạy MỘT lần sau khi app khởi động xong
public class AdminBootstrap implements ApplicationRunner {
  // [THÊM] Cùng giới hạn với RegisterRequest (8..72). 72 là giới hạn của BCrypt:
  // BCryptPasswordEncoder bản mới ném lỗi nếu mật khẩu dài quá 72 byte.
  private static final int MIN_PASSWORD = 8;
  private static final int MAX_PASSWORD = 72;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${cassin.admin.email:}")
  private String email;
  @Value("${cassin.admin.password:}")
  private String password;

  @Override
  @Transactional 
  public void run(ApplicationArguments args) {
    // 1. Không cấu hình gì → bỏ qua (máy dev bình thường, test...)
    if (email.isBlank() && password.isBlank()) {
      return;
    }
    // [THÊM] Cấu hình THIẾU MỘT NỬA (có email, quên password hoặc ngược lại) là gõ nhầm biến môi trường.
    // Trước đây cũng lặng lẽ bỏ qua → đăng nhập admin thất bại mà không biết vì sao.
    // Dừng app luôn ("fail fast") kèm câu báo rõ ràng, sửa 10 giây là xong.
    if (email.isBlank() || password.isBlank()) {
      throw new IllegalStateException("Phải đặt CẢ ADMIN_EMAIL và ADMIN_PASSWORD (hoặc bỏ trống cả hai)");
    }
    // [THÊM] Mật khẩu admin không qua RegisterRequest nên không có @Size kiểm tra hộ.
    // Không chặn thì ADMIN_PASSWORD=123 vẫn tạo được tài khoản có toàn quyền.
    if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
      throw new IllegalStateException(
          "ADMIN_PASSWORD phải từ " + MIN_PASSWORD + " đến " + MAX_PASSWORD + " ký tự");
    }

    String normalized = User.normalizeEmail(email);
    // 2. Kiểm tra email không được trùng
    if (userRepository.existsByEmail(normalized)) {
      // [THÊM] Ghi log để biết vì sao không có gì xảy ra. Hay gặp: email này đã đăng ký làm USER
      // thường từ trước → bootstrap KHÔNG tự nâng quyền (cố ý: tránh việc ai đó đăng ký trước đúng
      // email admin rồi chờ được nâng quyền). Muốn nâng thì sửa role trực tiếp trong DB.
      log.info("Bỏ qua tạo admin: email {} đã tồn tại", normalized);
      return;
    }

    // 3. Tạo tài khoản ADMIN
    User user = new User(normalized, passwordEncoder.encode(password), "Quản trị viên");
    user.promoteToAdmin();
    userRepository.save(user);

    // 4. Ghi log thông báo tạo admin   
    log.info("Đã tạo tài khoản admin {}", normalized);
  }
}

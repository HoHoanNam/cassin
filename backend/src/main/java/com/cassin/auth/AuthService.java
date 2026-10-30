package com.cassin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.auth.dto.LoginRequest;
import com.cassin.auth.dto.LoginResponse;
import com.cassin.auth.dto.RegisterRequest;
import com.cassin.common.BusinessException;
import com.cassin.common.PasswordRules;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
// Lombok sinh constructor cho các field final → Spring tự inject (Constructor Injection).
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  // [THÊM] Hai dependency cho đăng nhập
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;

  @Transactional
  public UserResponse register(RegisterRequest request) {
    // Chuẩn hóa TRƯỚC khi kiểm tra trùng: "An@Gmail.com" và "an@gmail.com" là một người.
    // Dùng chung hàm với entity User để hai nơi không bao giờ chuẩn hóa khác nhau.
    String email = User.normalizeEmail(request.email());

    // Kiểm tra email này đã tồn tại chưa? Lỗi do người dùng → BusinessException → 409.
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email này đã được đăng ký");
    }

    // [THÊM] @Size(max = 72) đếm KÝ TỰ, BCrypt giới hạn 72 BYTE: mật khẩu tiếng Việt có dấu
    // có thể qua được @Size nhưng làm encode() ném lỗi → 500. Xem giải thích trong PasswordRules.
    PasswordRules.requireBcryptLength(request.password());

    // Không bao giờ lưu mật khẩu gốc. encode() tạo chuỗi băm, mỗi lần một khác (có muối ngẫu nhiên).
    String passwordHash = passwordEncoder.encode(request.password());

    // 1. Tạo Entity
    User user = new User(email, passwordHash, request.displayName());
    // 2. Lưu User vào DB (Hibernate gán id vào chính object user)
    userRepository.save(user);
    // 3. Trả về UserResponse
    return UserResponse.from(user);
  }

  /**
   * [THÊM] Đăng nhập: kiểm tra email + mật khẩu, đúng thì cấp token.
   *
   *   1. Tìm user theo email (đã chuẩn hóa giống lúc đăng ký)
   *   2. So mật khẩu vừa nhập với password_hash trong DB
   *   3. Đúng cả hai → JwtService tạo token → trả về cho client giữ
   *
   * readOnly = true: method chỉ đọc DB, Hibernate bỏ qua bước theo dõi thay đổi → nhanh hơn.
   */
  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    String email = User.normalizeEmail(request.email());

    User user = userRepository.findByEmail(email)
        // passwordEncoder.matches(mậtKhẩuGốc, chuỗiBăm): BCrypt KHÔNG giải ngược được chuỗi băm.
        // Nó lấy "muối" nằm sẵn trong chuỗi băm, băm lại mật khẩu vừa nhập, rồi so hai kết quả.
        .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
        // Sai email hay sai mật khẩu đều trả CÙNG MỘT lỗi.
        // Nếu báo riêng "email không tồn tại", kẻ xấu thử lần lượt sẽ dò ra email nào đã đăng ký.
        .orElseThrow(() -> new BusinessException(
            HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng"));

    String token = jwtService.generate(user);
    return new LoginResponse(
        token,
        "Bearer",
        jwtProperties.accessTokenTtl().toSeconds(),
        UserResponse.from(user));
  }
}

package com.cassin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.auth.dto.AuthTokens;
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
  // [THÊM] Refresh token: phiên dài hạn, thu hồi được
  private final RefreshTokenService refreshTokenService;

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
   * Đăng nhập: kiểm tra email + mật khẩu, đúng thì cấp token.
   *
   *   1. Tìm user theo email (đã chuẩn hóa giống lúc đăng ký)
   *   2. So mật khẩu vừa nhập với password_hash trong DB
   *   3. Đúng cả hai → access token (JWT, sống access-token-ttl, trả trong body)
   *                   + refresh token (14 ngày, controller đặt vào cookie HttpOnly)
   *
   * [SỬA] readOnly = true → bỏ: đăng nhập giờ GHI một dòng refresh_tokens (mở phiên mới).
   */
  @Transactional
  public AuthTokens login(LoginRequest request, String userAgent) { // [SỬA] thêm userAgent (thiết bị)
    String email = User.normalizeEmail(request.email());

    User user = userRepository.findByEmail(email)
        // passwordEncoder.matches(mậtKhẩuGốc, chuỗiBăm): BCrypt KHÔNG giải ngược được chuỗi băm.
        // Nó lấy "muối" nằm sẵn trong chuỗi băm, băm lại mật khẩu vừa nhập, rồi so hai kết quả.
        .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
        // Sai email hay sai mật khẩu đều trả CÙNG MỘT lỗi.
        // Nếu báo riêng "email không tồn tại", kẻ xấu thử lần lượt sẽ dò ra email nào đã đăng ký.
        .orElseThrow(() -> new BusinessException(
            HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng"));

    return tokensFor(user, refreshTokenService.startSession(user, userAgent));
  }

  /**
   * [THÊM] Đổi refresh token (trong cookie) lấy cặp token mới. Token cũ bị thu hồi (xoay vòng).
   *
   * KHÔNG có @Transactional: rotate() tự mở transaction với noRollbackFor để lệnh "thu hồi cả họ"
   * vẫn được lưu dù sau đó ném 401. Nếu ở đây cũng mở transaction, rotate() chạy chung transaction này,
   * exception đi ngang qua sẽ đánh dấu rollback → lệnh thu hồi bị hủy. (Xem RefreshTokenService.rotate.)
   *
   * Role đọc lại từ DB (rotate join fetch user): bị hạ quyền thì access token mới phản ánh ngay.
   */
  public AuthTokens refresh(String refreshToken) {
    RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
    return tokensFor(rotation.user(), rotation.refreshToken());
  }

  /** [THÊM] Đăng xuất: thu hồi cả họ của refresh token đang cầm. Access token còn sống tối đa 15 phút. */
  public void logout(String refreshToken) {
    refreshTokenService.endSession(refreshToken);
  }

  private AuthTokens tokensFor(User user, String refreshToken) {
    LoginResponse body = new LoginResponse(
        jwtService.generate(user),
        "Bearer",
        jwtProperties.accessTokenTtl().toSeconds(),
        UserResponse.from(user));
    return new AuthTokens(body, refreshToken);
  }
}

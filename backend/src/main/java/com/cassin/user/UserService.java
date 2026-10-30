package com.cassin.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.PasswordRules;
import com.cassin.user.dto.ChangePasswordRequest;
import com.cassin.user.dto.UpdateProfileRequest;
import com.cassin.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * [THÊM] GET /api/me: chuyển từ UserController sang đây.
   * Trước đây controller tự gọi userRepository.findById(...), trong khi mọi controller khác
   * (Deck, Card, Review, Quiz...) chỉ gọi service. Gom về service để:
   *   - controller không phụ thuộc thẳng vào repository (một lớp, một việc)
   *   - lỗi "không tìm thấy tài khoản" chỉ viết MỘT chỗ (findUser), không lệch câu chữ
   *     (trước đây controller ghi "tài khoản", service ghi "người dùng")
   */
  @Transactional(readOnly = true)
  public UserResponse getMe(Long userId) {
    return UserResponse.from(findUser(userId));
  }

  @Transactional
  public UserResponse updateProfile(Long userId, UpdateProfileRequest r) {
    User user = findUser(userId);
    user.updateProfile(r.displayName(), r.bio());
    return UserResponse.from(user);
  }

  @Transactional
  public void changePassword(Long userId, ChangePasswordRequest r) {
    // 1. Kiểm tra tài khoản đã tồn tại chưa
    User user = findUser(userId);

    // [THÊM] Kiểm tra độ dài theo BYTE trước mọi thao tác BCrypt (xem PasswordRules).
    // Đặt TRƯỚC bước 2: bước 2 cũng đưa newPassword vào passwordEncoder.matches(...).
    PasswordRules.requireBcryptLength(r.newPassword());

    // 2. Xử lý trường hợp lỗi
    // matches(raw, hash): băm lại raw theo salt nằm trong hash rồi so sánh. Không
    // "giải mã" được hash.
    if (!passwordEncoder.matches(r.currentPassword(), user.getPasswordHash())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
    }

    if (passwordEncoder.matches(r.newPassword(), user.getPasswordHash())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", "Mật khẩu mới phải khác mật khẩu cũ");
    }

    // 3. Băm + Lưu mật khẩu mới
    user.changePassword(passwordEncoder.encode(r.newPassword()));
  }

  // HELPER
  private User findUser(Long userId) {
    return userRepository.findById(userId)
        // [SỬA] "người dùng" → "tài khoản": thống nhất câu chữ với API cũ (GET /api/me) và frontend.
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản"));
  }
}

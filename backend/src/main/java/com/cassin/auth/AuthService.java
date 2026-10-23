package com.cassin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.auth.dto.RegisterRequest;
import com.cassin.common.BusinessException;
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

  @Transactional
  public UserResponse register(RegisterRequest request) {
    // Chuẩn hóa TRƯỚC khi kiểm tra trùng: "An@Gmail.com" và "an@gmail.com" là một người.
    // Dùng chung hàm với entity User để hai nơi không bao giờ chuẩn hóa khác nhau.
    String email = User.normalizeEmail(request.email());

    // Kiểm tra email này đã tồn tại chưa? Lỗi do người dùng → BusinessException → 409.
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email này đã được đăng ký");
    }

    // Không bao giờ lưu mật khẩu gốc. encode() tạo chuỗi băm, mỗi lần một khác (có muối ngẫu nhiên).
    String passwordHash = passwordEncoder.encode(request.password());

    // 1. Tạo Entity
    User user = new User(email, passwordHash, request.displayName());
    // 2. Lưu User vào DB (Hibernate gán id vào chính object user)
    userRepository.save(user);
    // 3. Trả về UserResponse
    return UserResponse.from(user);
  }
}

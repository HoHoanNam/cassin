package com.cassin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [THÊM] Unit test cho AdminBootstrap.
 *
 * Field email/password được Spring điền bằng @Value lúc chạy thật. Trong unit test không có Spring,
 * nên tự điền bằng ReflectionTestUtils.setField (giống cách gán id giả cho entity).
 * run(null): method không dùng tới ApplicationArguments nên truyền null được.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {
  @Mock UserRepository userRepository;
  @Mock PasswordEncoder passwordEncoder;

  private AdminBootstrap bootstrap;

  @BeforeEach
  void setUp() {
    bootstrap = new AdminBootstrap(userRepository, passwordEncoder);
  }

  @Test
  @DisplayName("Không cấu hình gì: bỏ qua, không đụng DB")
  void noConfig_doesNothing() {
    configure("", "");

    bootstrap.run(null);

    verifyNoInteractions(userRepository, passwordEncoder);
  }

  @Test
  @DisplayName("Chỉ có email, quên password: dừng app kèm thông báo rõ ràng")
  void halfConfig_failsFast() {
    configure("admin@cassin.vn", "");

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_PASSWORD");
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("Mật khẩu admin quá ngắn: dừng app, không tạo tài khoản yếu")
  void shortPassword_failsFast() {
    configure("admin@cassin.vn", "123");

    assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(userRepository, passwordEncoder);
  }

  @Test
  @DisplayName("Email đã tồn tại: không tạo lại, không băm mật khẩu")
  void existingEmail_isSkipped() {
    configure("admin@cassin.vn", "secret-123");
    when(userRepository.existsByEmail("admin@cassin.vn")).thenReturn(true);

    bootstrap.run(null);

    verify(userRepository, never()).save(any());
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("Tạo admin: email chuẩn hóa, mật khẩu đã băm, role = ADMIN")
  void createsAdmin() {
    configure("  Admin@Cassin.VN ", "secret-123");
    when(userRepository.existsByEmail("admin@cassin.vn")).thenReturn(false);
    when(passwordEncoder.encode("secret-123")).thenReturn("bcrypt-hash");

    bootstrap.run(null);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("admin@cassin.vn");
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");   // không lưu mật khẩu gốc
    assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
  }

  private void configure(String email, String password) {
    ReflectionTestUtils.setField(bootstrap, "email", email);
    ReflectionTestUtils.setField(bootstrap, "password", password);
  }
}

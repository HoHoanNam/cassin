package com.cassin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.auth.RefreshTokenService;
import com.cassin.common.BusinessException;
import com.cassin.user.dto.ChangePasswordRequest;

/** UserService.changePassword: đổi mật khẩu xong thì đăng xuất mọi thiết bị, mở phiên mới cho thiết bị hiện tại. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  private static final Long USER_ID = 1L;

  @Mock UserRepository userRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock RefreshTokenService refreshTokenService;

  @InjectMocks UserService userService;

  private User user() {
    User user = new User("nam@gmail.com", "old-hash", "Nam");
    ReflectionTestUtils.setField(user, "id", USER_ID);
    return user;
  }

  @Test
  void changePassword_ok_revokesAllSessionsThenOpensNewOne() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("matkhaucu", "old-hash")).thenReturn(true);
    when(passwordEncoder.matches("matkhaumoi", "old-hash")).thenReturn(false);
    when(passwordEncoder.encode("matkhaumoi")).thenReturn("new-hash");
    when(refreshTokenService.startSession(user)).thenReturn("refresh-raw");

    String refreshToken = userService.changePassword(USER_ID, new ChangePasswordRequest("matkhaucu", "matkhaumoi"));

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    assertThat(refreshToken).isEqualTo("refresh-raw");
    // Thứ tự quan trọng: mở phiên mới TRƯỚC rồi thu hồi tất cả thì phiên mới cũng bị thu hồi luôn.
    InOrder order = inOrder(refreshTokenService);
    order.verify(refreshTokenService).revokeAll(USER_ID);
    order.verify(refreshTokenService).startSession(user);
  }

  @Test
  void changePassword_wrongCurrentPassword_keepsAllSessions() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("sai", "old-hash")).thenReturn(false);

    Throwable thrown = catchThrowable(() ->
        userService.changePassword(USER_ID, new ChangePasswordRequest("sai", "matkhaumoi")));

    assertThat(thrown).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) thrown).getCode()).isEqualTo("WRONG_PASSWORD");
    assertThat(user.getPasswordHash()).isEqualTo("old-hash");
    verifyNoInteractions(refreshTokenService);
  }
}

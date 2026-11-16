package com.cassin.auth;

import static com.cassin.auth.RefreshTokenServiceTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.auth.dto.AuthTokens;
import com.cassin.auth.dto.LoginRequest;
import com.cassin.user.User;
import com.cassin.user.UserRepository;

/** AuthService: đăng nhập mở phiên, refresh, đăng xuất. Mọi phụ thuộc là MOCK. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock UserRepository userRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock JwtService jwtService;
  @Mock JwtProperties jwtProperties;
  @Mock RefreshTokenService refreshTokenService;

  // Constructor injection theo KIỂU: không phụ thuộc thứ tự field trong AuthService.
  @InjectMocks AuthService authService;

  private final User user = AuthFixtures.user();

  @Test
  void login_ok_accessTokenInBody_refreshTokenReturnedSeparately() {
    when(userRepository.findByEmail("nam@gmail.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("12345678", user.getPasswordHash())).thenReturn(true);
    when(jwtService.generate(user)).thenReturn("access-jwt");
    when(jwtProperties.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    when(refreshTokenService.startSession(user)).thenReturn("refresh-raw");

    AuthTokens tokens = authService.login(new LoginRequest(" Nam@Gmail.com ", "12345678"));

    assertThat(tokens.body().accessToken()).isEqualTo("access-jwt");
    assertThat(tokens.body().tokenType()).isEqualTo("Bearer");
    assertThat(tokens.body().expiresIn()).isEqualTo(900);         // 15 phút
    assertThat(tokens.body().user().email()).isEqualTo("nam@gmail.com");
    assertThat(tokens.refreshToken()).isEqualTo("refresh-raw");   // controller đặt vào cookie, KHÔNG nằm trong body
  }

  @Test
  void login_wrongPassword_doesNotOpenSession() {
    when(userRepository.findByEmail("nam@gmail.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("sai-mat-khau", user.getPasswordHash())).thenReturn(false);

    assertError(() -> authService.login(new LoginRequest("nam@gmail.com", "sai-mat-khau")),
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    verify(refreshTokenService, never()).startSession(any());
  }

  @Test
  void login_unknownEmail_sameErrorAsWrongPassword() {
    when(userRepository.findByEmail("ai@gmail.com")).thenReturn(Optional.empty());

    assertError(() -> authService.login(new LoginRequest("ai@gmail.com", "12345678")),
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    verify(refreshTokenService, never()).startSession(any());
  }

  @Test
  void refresh_rotatesToken_andIssuesAccessTokenFromCurrentUserState() {
    user.promoteToAdmin();   // role trong DB đã đổi kể từ lần đăng nhập trước
    when(refreshTokenService.rotate("old-raw")).thenReturn(new RefreshTokenService.Rotation(user, "new-raw"));
    when(jwtService.generate(user)).thenReturn("access-jwt-2");
    when(jwtProperties.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

    AuthTokens tokens = authService.refresh("old-raw");

    assertThat(tokens.body().accessToken()).isEqualTo("access-jwt-2");
    assertThat(tokens.body().user().role()).isEqualTo(com.cassin.user.Role.ADMIN);
    assertThat(tokens.refreshToken()).isEqualTo("new-raw");
  }

  @Test
  void refresh_errorFromRotate_propagates() {
    var reused = new com.cassin.common.BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_REUSED", "x");
    when(refreshTokenService.rotate("old-raw")).thenThrow(reused);

    assertError(() -> authService.refresh("old-raw"), HttpStatus.UNAUTHORIZED, "REFRESH_REUSED");
    verify(jwtService, never()).generate(any());
  }

  @Test
  void refresh_mustNotOpenItsOwnTransaction() throws NoSuchMethodException {
    // Nếu refresh có @Transactional, rotate() chạy chung transaction; exception REFRESH_REUSED đi ngang qua
    // sẽ đánh dấu rollback → lệnh "thu hồi cả họ" bị hủy. Xem Javadoc RefreshTokenService.rotate.
    assertThat(AuthService.class.getMethod("refresh", String.class).isAnnotationPresent(Transactional.class)).isFalse();
    assertThat(AuthService.class.isAnnotationPresent(Transactional.class)).isFalse();
  }

  @Test
  void logout_endsSessionOfPresentedToken() {
    authService.logout("raw");

    verify(refreshTokenService).endSession("raw");
  }
}

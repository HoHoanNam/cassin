package com.cassin.auth;

import static com.cassin.auth.AuthFixtures.FAMILY;
import static com.cassin.auth.AuthFixtures.NOW;
import static com.cassin.auth.AuthFixtures.RAW;
import static com.cassin.auth.AuthFixtures.TTL;
import static com.cassin.auth.AuthFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.auth.dto.RefreshTokenProperties;
import com.cassin.common.BusinessException;
import com.cassin.user.User;

/**
 * RefreshTokenService. Repository là MOCK: các câu UPDATE có điều kiện (revokeIfActive...) được giả lập
 * bằng giá trị trả về (1 = thắng, 0 = thua). Hành vi SQL thật cần test tích hợp với PostgreSQL (Testcontainers).
 *
 * Bảng kết quả của rotate: xem Javadoc của RefreshTokenService.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
  @Mock RefreshTokenRepository repository;

  private RefreshTokenService service;
  private final User user = AuthFixtures.user();

  @BeforeEach
  void setUp() {
    service = new RefreshTokenService(
        repository, new RefreshTokenProperties(TTL, false), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  // ===== startSession =====

  @Test
  void startSession_storesOnlyHash_andReturnsRawToken() {
    String raw = service.startSession(user);

    RefreshToken saved = savedToken();
    assertThat(raw).hasSize(43);                                     // 32 byte, Base64 URL không dấu "="
    assertThat(raw).matches("[A-Za-z0-9_-]+");                      // đặt vào cookie không cần mã hóa
    assertThat(saved.getTokenHash()).isNotEqualTo(raw);              // DB KHÔNG giữ token gốc
    assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw));
    assertThat(saved.getUser()).isSameAs(user);
    assertThat(saved.getFamilyId()).isNotNull();
    assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(TTL));
  }

  @Test
  void startSession_eachLoginOpensNewFamilyWithDifferentToken() {
    String first = service.startSession(user);
    String second = service.startSession(user);

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(repository, times(2)).save(captor.capture());
    List<RefreshToken> saved = captor.getAllValues();
    assertThat(first).isNotEqualTo(second);
    assertThat(saved.get(0).getFamilyId()).isNotEqualTo(saved.get(1).getFamilyId());
  }

  @Test
  void hash_isSha256Hex_andDeterministic() {
    assertThat(RefreshTokenService.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")   // SHA-256 chuẩn của "abc"
        .isEqualTo(RefreshTokenService.hash("abc"));
  }

  // ===== rotate: thành công =====

  @Test
  void rotate_validToken_revokesItAndIssuesNewTokenInSameFamily() {
    RefreshToken current = AuthFixtures.activeToken(user);
    when(repository.findByTokenHashWithUser(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(current));
    when(repository.revokeIfActive(10L, NOW)).thenReturn(1);

    RefreshTokenService.Rotation rotation = service.rotate(RAW);

    assertThat(rotation.user()).isSameAs(user);
    assertThat(rotation.refreshToken()).isNotEqualTo(RAW);
    RefreshToken next = savedToken();
    assertThat(next.getFamilyId()).isEqualTo(FAMILY);               // cùng họ
    assertThat(next.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.refreshToken()));
    assertThat(next.getExpiresAt()).isEqualTo(NOW.plus(TTL));       // hạn mới tính từ lúc refresh
    verify(repository).revokeIfActive(10L, NOW);
  }

  // ===== rotate: lỗi =====

  @Test
  void rotate_missingOrBlankCookie_isInvalid_withoutTouchingDb() {
    assertError(() -> service.rotate(null), HttpStatus.UNAUTHORIZED, "REFRESH_INVALID");
    assertError(() -> service.rotate("  "), HttpStatus.UNAUTHORIZED, "REFRESH_INVALID");
    verifyNoInteractions(repository);
  }

  @Test
  void rotate_unknownToken_isInvalid() {
    when(repository.findByTokenHashWithUser(any())).thenReturn(Optional.empty());

    assertError(() -> service.rotate(RAW), HttpStatus.UNAUTHORIZED, "REFRESH_INVALID");
    verify(repository, never()).save(any());
  }

  @Test
  void rotate_expiredToken_isExpired_andNotRevokedOrReplaced() {
    when(repository.findByTokenHashWithUser(RefreshTokenService.hash(RAW)))
        .thenReturn(Optional.of(AuthFixtures.expiredToken(user)));

    assertError(() -> service.rotate(RAW), HttpStatus.UNAUTHORIZED, "REFRESH_EXPIRED");
    verify(repository, never()).revokeIfActive(anyLong(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void rotate_tokenRevokedLongAgo_isReuse_revokesWholeFamily() {
    when(repository.findByTokenHashWithUser(RefreshTokenService.hash(RAW)))
        .thenReturn(Optional.of(AuthFixtures.revokedToken(user, Duration.ofHours(1))));

    assertError(() -> service.rotate(RAW), HttpStatus.UNAUTHORIZED, "REFRESH_REUSED");
    verify(repository).revokeFamily(FAMILY, NOW);                  // kẻ cầm token mới nhất cũng bị đá ra
    verify(repository, never()).save(any());
  }

  @Test
  void rotate_tokenRotatedSecondsAgo_isRace_notTheft() {
    // Vừa bị XOAY: họ vẫn còn token sống (token mà tab kia vừa nhận).
    when(repository.findByTokenHashWithUser(RefreshTokenService.hash(RAW)))
        .thenReturn(Optional.of(AuthFixtures.revokedToken(user, Duration.ofSeconds(3))));
    when(repository.existsByFamilyIdAndRevokedAtIsNull(FAMILY)).thenReturn(true);

    assertError(() -> service.rotate(RAW), HttpStatus.CONFLICT, "REFRESH_RACE");
    verify(repository, never()).revokeFamily(any(), any());       // KHÔNG khóa cả họ
    verify(repository, never()).save(any());
  }

  @Test
  void rotate_tokenRevokedSecondsAgoByLogout_isUnauthorized_notRace() {
    // Vừa bị THU HỒI (đăng xuất / đổi mật khẩu): họ không còn token sống → không phải chạy đua.
    when(repository.findByTokenHashWithUser(RefreshTokenService.hash(RAW)))
        .thenReturn(Optional.of(AuthFixtures.revokedToken(user, Duration.ofSeconds(3))));
    when(repository.existsByFamilyIdAndRevokedAtIsNull(FAMILY)).thenReturn(false);

    assertError(() -> service.rotate(RAW), HttpStatus.UNAUTHORIZED, "REFRESH_REUSED");
    verify(repository, never()).save(any());
  }

  @Test
  void rotate_lostCompareAndSet_isRace_andIssuesNothing() {
    // Hai request cùng đọc thấy token còn hiệu lực; request kia UPDATE trước → câu của mình đổi 0 dòng.
    when(repository.findByTokenHashWithUser(RefreshTokenService.hash(RAW)))
        .thenReturn(Optional.of(AuthFixtures.activeToken(user)));
    when(repository.revokeIfActive(10L, NOW)).thenReturn(0);

    assertError(() -> service.rotate(RAW), HttpStatus.CONFLICT, "REFRESH_RACE");
    verify(repository, never()).save(any());
  }

  @Test
  void rotate_keepsFamilyRevocationWhenThrowing() throws NoSuchMethodException {
    // Phát hiện dùng lại token: thu hồi cả họ RỒI ném 401. Thiếu noRollbackFor thì transaction rollback,
    // lệnh thu hồi mất theo. Test này chốt cấu hình đó (mock không kiểm tra được transaction thật).
    Transactional tx = RefreshTokenService.class.getMethod("rotate", String.class).getAnnotation(Transactional.class);

    assertThat(tx).isNotNull();
    assertThat(tx.noRollbackFor()).contains(BusinessException.class);
  }

  // ===== đăng xuất, đổi mật khẩu, dọn dẹp =====

  @Test
  void endSession_revokesWholeFamilyOfPresentedToken() {
    when(repository.findByTokenHashWithUser(RefreshTokenService.hash(RAW)))
        .thenReturn(Optional.of(AuthFixtures.activeToken(user)));

    service.endSession(RAW);

    verify(repository).revokeFamily(FAMILY, NOW);
  }

  @Test
  void endSession_unknownOrMissingToken_doesNothing() {
    when(repository.findByTokenHashWithUser(any())).thenReturn(Optional.empty());

    service.endSession(RAW);
    service.endSession(null);

    verify(repository, never()).revokeFamily(any(), any());
  }

  @Test
  void revokeAll_revokesEveryActiveTokenOfUser() {
    service.revokeAll(USER_ID);

    verify(repository).revokeAllForUser(USER_ID, NOW);
  }

  @Test
  void deleteExpired_deletesTokensExpiredBeforeNow() {
    when(repository.deleteExpired(NOW)).thenReturn(5);

    assertThat(service.deleteExpired()).isEqualTo(5);
  }

  // ===== helper =====

  private RefreshToken savedToken() {
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }

  static void assertError(Runnable call, HttpStatus status, String code) {
    Throwable thrown = catchThrowable(call::run);
    assertThat(thrown).as("phải ném BusinessException %s", code).isInstanceOf(BusinessException.class);
    BusinessException ex = (BusinessException) thrown;
    assertThat(ex.getStatus()).isEqualTo(status);
    assertThat(ex.getCode()).isEqualTo(code);
  }
}

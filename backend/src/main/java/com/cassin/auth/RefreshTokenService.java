package com.cassin.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.auth.dto.RefreshTokenProperties;
import com.cassin.auth.dto.SessionResponse;
import com.cassin.common.BusinessException;
import com.cassin.user.User;

import lombok.RequiredArgsConstructor;

// [THÊM] Javadoc: bảng kết quả của rotate
/**
 * Cấp, xoay vòng và thu hồi refresh token.
 *
 *   startSession   đăng nhập / đổi mật khẩu → mở họ token mới
 *   rotate         POST /api/auth/refresh → thu hồi token cũ, cấp token mới cùng họ
 *   endSession     POST /api/auth/logout  → thu hồi cả họ của token đang cầm
 *   revokeAll      đổi mật khẩu           → thu hồi mọi phiên của người dùng
 *   [THÊM] listSessions / revokeSession / revokeOtherSessions: màn hình "Thiết bị đang đăng nhập"
 *
 * Kết quả của rotate:
 *
 *   | Token gửi lên                             | Kết quả                                           |
 *   |-------------------------------------------|---------------------------------------------------|
 *   | không có / không tìm thấy                 | 401 REFRESH_INVALID                               |
 *   | vừa bị XOAY < 10 giây trước               | 409 REFRESH_RACE (tab khác vừa refresh, thử lại)  |
 *   | đã thu hồi (lâu hơn, hoặc do đăng xuất)   | thu hồi CẢ HỌ + 401 REFRESH_REUSED                |
 *   | hết hạn                                   | 401 REFRESH_EXPIRED                               |
 *   | hợp lệ nhưng request khác vừa xoay trước  | 409 REFRESH_RACE                                  |
 *   | hợp lệ                                    | token mới                                         |
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
  /** Khoảng thời gian coi "token vừa bị xoay" là hai request chạy đua, không phải bị đánh cắp. */
  static final Duration RACE_WINDOW = Duration.ofSeconds(10);
  // [SỬA] Thiếu hằng này → issue() không biên dịch được ("cannot find symbol TOKEN_BYTES").
  private static final int TOKEN_BYTES = 32; // 256 bit ngẫu nhiên

  // GIỮ ĐÚNG THỨ TỰ 3 field này: test gọi new RefreshTokenService(repository, properties, clock)
  private final RefreshTokenRepository repository;
  private final RefreshTokenProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom(); // không final-inject: có giá trị sẵn

  public record Rotation(User user, String refreshToken) {
  }

  /**
   * Mở phiên mới (họ token mới). Trả về token GỐC để đặt vào cookie; DB chỉ giữ bản băm.
   * [SỬA] Nhận thêm userAgent (header của request đăng nhập) để hiển thị thiết bị; null nếu không có.
   */
  @Transactional
  public String startSession(User user, String userAgent) {
    Instant now = clock.instant();
    return issue(user, UUID.randomUUID(), now, userAgent, now);
  }

  /** SHA-256 → 64 ký tự hex. Package-private để test kiểm tra DB chỉ nhận bản băm. */
  static String hash(String rawToken) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM không có SHA-256", e); // mọi JVM chuẩn đều có
    }
  }

  // [SỬA] thêm userAgent + signedInAt: đăng nhập thì lấy mới, refresh thì chép từ token cũ
  private String issue(User user, UUID familyId, Instant now, String userAgent, Instant signedInAt) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    // Base64 URL-safe, bỏ dấu "=": 43 ký tự, đặt vào cookie không cần mã hóa thêm.
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    repository.save(RefreshToken.issue(user, hash(raw), familyId, now, properties.ttl(), userAgent, signedInAt));
    return raw;
  }

  private Optional<RefreshToken> find(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return repository.findByTokenHashWithUser(hash(rawToken));
  }

  // [THÊM]
  /**
   * noRollbackFor: phát hiện dùng lại token thì phải THU HỒI CẢ HỌ rồi mới ném 401.
   * Mặc định @Transactional rollback khi gặp RuntimeException → lệnh thu hồi bị hủy theo,
   * kẻ cầm token mới nhất vẫn dùng tiếp được. Giữ lại thay đổi dù có ném BusinessException.
   *
   * Vì thế AuthService.refresh (bên gọi) KHÔNG được mở transaction riêng: nếu có, method này chạy
   * chung transaction bên ngoài, exception đi qua bên ngoài sẽ đánh dấu rollback và hủy luôn lệnh thu hồi.
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public Rotation rotate(String rawToken) {
    RefreshToken current = find(rawToken)
        .orElseThrow(() -> unauthorized("REFRESH_INVALID", "Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại"));
    Instant now = clock.instant();

    if (current.isRevoked()) {
      // Vừa bị thu hồi (< 10 giây) VÀ họ vẫn còn token sống → nó vừa bị XOAY: hai tab gần như cùng lúc
      // gửi cùng một cookie, tab kia thắng, trình duyệt đã có cookie mới. Thử lại là được.
      // Họ không còn token sống → nó bị THU HỒI (đăng xuất, đổi mật khẩu): không phải chạy đua → 401.
      if (current.revokedWithin(RACE_WINDOW, now)
          && repository.existsByFamilyIdAndRevokedAtIsNull(current.getFamilyId())) {
        throw race();
      }
      // Token cũ bị dùng lại: hoặc người dùng thật, hoặc kẻ đánh cắp đang cầm token cũ.
      // Không biết ai là ai → khóa cả họ, cả hai đều phải đăng nhập lại.
      repository.revokeFamily(current.getFamilyId(), now);
      throw unauthorized("REFRESH_REUSED", "Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại");
    }
    if (current.isExpired(now)) {
      throw unauthorized("REFRESH_EXPIRED", "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
    }
    // Compare-and-set: chỉ một trong hai request chạy song song thu hồi được token này.
    if (repository.revokeIfActive(current.getId(), now) == 0) {
      throw race();
    }
    // [SỬA] Token mới thừa hưởng thiết bị + thời điểm đăng nhập: cả họ vẫn là MỘT dòng "thiết bị"
    String next = issue(current.getUser(), current.getFamilyId(), now,
        current.getUserAgent(), current.getSignedInAt());
    return new Rotation(current.getUser(), next);
  }

  // [THÊM]
  /** Đăng xuất: thu hồi cả họ của token đang cầm. Token rỗng / không tìm thấy thì bỏ qua (đăng xuất luôn thành công). */
  @Transactional
  public void endSession(String rawToken) {
    find(rawToken).ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
  }

  // [THÊM]
  /** Thu hồi mọi phiên của người dùng (đổi mật khẩu: đăng xuất mọi thiết bị). */
  @Transactional
  public void revokeAll(Long userId) {
    repository.revokeAllForUser(userId, clock.instant());
  }

  // ===== [THÊM] Quản lý phiên: GET / DELETE /api/auth/sessions =====

  /**
   * Thiết bị đang đăng nhập của userId. current = thiết bị đang gửi request (cookie thuộc họ đó).
   * Không có cookie (hoặc cookie không thuộc user này) → không dòng nào là current.
   */
  @Transactional(readOnly = true)
  public List<SessionResponse> listSessions(Long userId, String rawToken) {
    UUID current = currentFamily(userId, rawToken);
    return repository.findActiveSessions(userId, clock.instant()).stream()
        .map(token -> SessionResponse.of(token, token.getFamilyId().equals(current)))
        .toList();
  }

  /**
   * Đăng xuất một thiết bị. Họ không tồn tại / đã thu hồi / của người khác → CÙNG một lỗi 404.
   *
   * @return true nếu vừa đăng xuất CHÍNH thiết bị đang dùng → controller xóa luôn cookie
   */
  @Transactional
  public boolean revokeSession(Long userId, UUID familyId, String rawToken) {
    if (repository.revokeFamilyOfUser(familyId, userId, clock.instant()) == 0) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy phiên đăng nhập");
    }
    return familyId.equals(currentFamily(userId, rawToken));
  }

  /**
   * Đăng xuất mọi thiết bị KHÁC, giữ thiết bị đang dùng.
   * Không nhận ra thiết bị đang dùng (thiếu cookie) → đăng xuất tất cả: không còn phiên nào để giữ.
   *
   * @return số thiết bị vừa bị đăng xuất
   */
  @Transactional
  public int revokeOtherSessions(Long userId, String rawToken) {
    UUID keep = currentFamily(userId, rawToken);
    Instant now = clock.instant();
    return keep == null
        ? repository.revokeAllForUser(userId, now)
        : repository.revokeOthers(userId, keep, now);
  }

  /** Họ của cookie đang gửi lên, chỉ khi cookie thuộc đúng user đang đăng nhập. */
  private UUID currentFamily(Long userId, String rawToken) {
    return find(rawToken)
        .filter(token -> token.getUser().getId().equals(userId))
        .map(RefreshToken::getFamilyId)
        .orElse(null);
  }

  // [THÊM]
  /** Xóa token đã hết hạn. Gọi từ RefreshTokenCleanup theo lịch. */
  @Transactional
  public int deleteExpired() {
    return repository.deleteExpired(clock.instant());
  }

  // [THÊM] helper tạo lỗi cho rotate
  private static BusinessException unauthorized(String code, String message) {
    return new BusinessException(HttpStatus.UNAUTHORIZED, code, message);
  }

  private static BusinessException race() {
    return new BusinessException(HttpStatus.CONFLICT, "REFRESH_RACE", "Phiên vừa được làm mới ở nơi khác, vui lòng thử lại");
  }
}

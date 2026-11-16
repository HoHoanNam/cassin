package com.cassin.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /**
   * join fetch user: refresh xong phải cấp access token mới (cần id, email, role)
   * → lấy luôn user trong cùng một câu SQL. Role đọc từ DB nên bị hạ quyền là token mới phản ánh ngay.
   */
  @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
  Optional<RefreshToken> findByTokenHashWithUser(@Param("hash") String tokenHash);

  /** Họ này còn token nào sống không (phân biệt "vừa bị xoay" với "đã bị thu hồi"). Spring Data tự sinh câu SQL từ tên method. */
  boolean existsByFamilyIdAndRevokedAtIsNull(UUID familyId);

  /**
   * Thu hồi MỘT token, CHỈ KHI nó chưa bị thu hồi. Trả về số dòng đã đổi (0 hoặc 1).
   *
   * Đây là phép "so sánh rồi gán" (compare-and-set) nguyên tử trong DB:
   * hai request cùng refresh một token → cả hai chạy câu này, PostgreSQL khóa dòng cho câu UPDATE
   * nên chỉ một câu thấy "revoked_at is null" và đổi được 1 dòng; câu kia đổi 0 dòng → thua.
   *
   * flushAutomatically: ghi mọi thay đổi đang chờ xuống DB trước khi chạy câu UPDATE.
   * Lưu ý: câu UPDATE đi thẳng xuống DB, entity RefreshToken đang nằm trong bộ nhớ KHÔNG được cập nhật
   * (revokedAt vẫn null). Ở đây không đọc lại token sau đó nên không sao. Không dùng clearAutomatically:
   * nó tách mọi entity khỏi phiên làm việc, kể cả User vừa join fetch mà token mới còn cần gắn vào.
   */
  @Modifying(flushAutomatically = true)
  @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
  int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);

  /** Thu hồi mọi token còn hiệu lực của một họ (dùng lại token cũ, đăng xuất). */
  @Modifying(flushAutomatically = true)
  @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

  /** Thu hồi mọi phiên của một người (đổi mật khẩu). */
  @Modifying(flushAutomatically = true)
  @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
  int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

  /** Xóa token đã hết hạn (job dọn dẹp). Token hết hạn thì đằng nào cũng không dùng được nữa. */
  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);
}

package com.cassin.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.cassin.auth.DeviceLabel;
import com.cassin.auth.RefreshToken;

/**
 * [THÊM] Một thiết bị đang đăng nhập (GET /api/auth/sessions).
 *
 * @param id           family_id của họ token. Ổn định qua mọi lần refresh, dùng để đăng xuất thiết bị đó.
 *                     Biết id KHÔNG làm được gì khác: muốn refresh vẫn phải có token gốc trong cookie.
 * @param lastActiveAt created_at của token đang sống = lần refresh gần nhất (sai lệch tối đa bằng TTL access token)
 * @param current      true nếu đây là thiết bị đang gửi request (cookie thuộc họ này)
 */
public record SessionResponse(
    UUID id,
    String browser,
    String os,
    Instant signedInAt,
    Instant lastActiveAt,
    boolean current) {

  public static SessionResponse of(RefreshToken token, boolean current) {
    DeviceLabel device = DeviceLabel.parse(token.getUserAgent());
    return new SessionResponse(token.getFamilyId(), device.browser(), device.os(),
        token.getSignedInAt(), token.getCreatedAt(), current);
  }
}

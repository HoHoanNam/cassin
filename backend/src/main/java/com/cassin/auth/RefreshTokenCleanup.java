package com.cassin.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mỗi đêm xóa refresh token đã hết hạn, để bảng không phình mãi.
 *
 * Chỉ xóa token HẾT HẠN, không xóa token đã thu hồi mà còn hạn: còn giữ thì mới nhận ra được
 * "token cũ bị dùng lại" (RefreshTokenService.rotate). Xóa sớm thì lần dùng lại đó chỉ còn là
 * "token không tồn tại", mất cơ hội khóa cả họ.
 *
 * Lưu ý khi chạy nhiều instance: mỗi instance đều chạy job này. Câu DELETE chạy hai lần vẫn đúng
 * (lần sau xóa 0 dòng), nên chưa cần khóa phân tán (ShedLock).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanup {
  private final RefreshTokenService refreshTokenService;

  @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")   // 3:30 sáng mỗi ngày
  public void deleteExpiredTokens() {
    int deleted = refreshTokenService.deleteExpired();
    log.info("Đã xóa {} refresh token hết hạn", deleted);
  }
}

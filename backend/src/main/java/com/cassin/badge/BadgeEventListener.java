package com.cassin.badge;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cassin.badge.event.BadgeTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nghe MỌI sự kiện implements BadgeTrigger.
 *
 *   AFTER_COMMIT       : chỉ xét khi hành động học tập đã lưu thật (rollback thì không cấp huy hiệu "ma").
 *   fallbackExecution  : sự kiện phát ngoài transaction (hiếm) vẫn được xử lý thay vì bị bỏ qua im lặng.
 *   @Async             : chạy ở thread khác → request của người học không phải chờ đếm huy hiệu.
 *   try/catch          : lỗi huy hiệu chỉ ghi log, không bao giờ làm hỏng hành động chính (đã commit rồi).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BadgeEventListener {
  private final BadgeAwardService awardService;

  @Async(AsyncConfig.BADGE_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(BadgeTrigger event) {
    try {
      awardService.evaluate(event);
    } catch (RuntimeException ex) {
      log.error("Không xét được huy hiệu cho sự kiện {}", event, ex);
    }
  }
}

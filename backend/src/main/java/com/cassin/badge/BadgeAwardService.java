package com.cassin.badge;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.badge.event.BadgeTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cấp huy hiệu: với mỗi rule quan tâm sự kiện → tính tiến độ → cấp MỌI cấp đã đủ mốc mà chưa có.
 *
 * Nhảy nhiều cấp một lúc (vd nhập CSV 120 từ: 0 → cấp I và II) thì cấp đủ từng cấp; popup chỉ hiện cấp cao nhất
 * (BadgeService.unseen lo phần đó). Chạy lại bao nhiêu lần cũng an toàn: award() bỏ qua cấp đã có.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeAwardService {
  private final BadgeRuleRegistry registry;
  private final BadgeTierRepository tierRepository;
  private final UserBadgeRepository userBadgeRepository;
  private final Clock clock;

  /** Các cấp VỪA được cấp trong lần gọi này (dùng cho log và test). */
  public record Awarded(String code, int level) {
  }

  /**
   * REQUIRES_NEW, KHÔNG phải REQUIRED: method này được gọi từ listener AFTER_COMMIT. Lúc đó transaction của
   * hành động học tập đã commit nhưng vẫn còn "gắn" vào thread (khi chạy đồng bộ, vd trong test).
   * REQUIRED sẽ nhập vào transaction đã xong đó → các INSERT không bao giờ được commit, huy hiệu mất im lặng.
   * REQUIRES_NEW luôn mở transaction mới, đúng cả khi chạy @Async lẫn đồng bộ.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Awarded> evaluate(BadgeTrigger event) {
    List<Awarded> awarded = new ArrayList<>();
    for (BadgeRule rule : registry.listeningTo(event)) {
      awarded.addAll(evaluate(rule, event.userId()));
    }
    if (!awarded.isEmpty()) {
      log.info("Cấp huy hiệu cho user {}: {}", event.userId(), awarded);
    }
    return awarded;
  }

  /** Xét một huy hiệu cho một người, bỏ qua sự kiện (dùng khi cần chấm lại, vd job bù dữ liệu cũ). */
  @Transactional
  public List<Awarded> evaluate(BadgeRule rule, Long userId) {
    long progress = rule.progress(userId);
    Instant now = clock.instant();
    List<Awarded> awarded = new ArrayList<>();
    for (BadgeTier tier : tierRepository.findByCode(rule.code())) {
      if (progress < tier.getTarget()) {
        break; // mốc tăng dần (V18 tự kiểm) → cấp này chưa đạt thì cấp sau cũng chưa
      }
      if (userBadgeRepository.award(userId, rule.code(), (short) tier.level(), now) == 1) {
        awarded.add(new Awarded(rule.code(), tier.level()));
      }
    }
    return awarded;
  }
}
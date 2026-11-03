package com.cassin.practice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.practice.dto.MistakeItem;

import lombok.RequiredArgsConstructor;

/**
 * Sổ lỗi sai: các nghĩa user luyện sai nhiều nhất trong N ngày gần đây.
 */
@Service
@RequiredArgsConstructor
public class MistakeService {
  static final int MAX_DAYS = 30;
  static final int MAX_LIMIT = 50;

  private final ReviewEventRepository eventRepository;
  private final Clock clock;

  /** GET /api/mistakes?days=&limit= */
  @Transactional(readOnly = true)
  public List<MistakeItem> list(Long userId, int days, int limit) {
    // 1. Số ngày do user CHỌN trên giao diện → sai thì báo lỗi rõ ràng (400), không âm thầm sửa.
    if (days < 1 || days > MAX_DAYS) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAYS", "Số ngày phải từ 1 đến " + MAX_DAYS);
    }

    // 2. Mốc thời gian: đúng thời điểm này, lùi lại `days` ngày (tính tròn 24 giờ mỗi ngày).
    Instant since = Instant.now(clock).minus(Duration.ofDays(days));

    // 3. Số dòng tối đa → ép vào [1, 50], giống cách ReviewService xử lý newLimit.
    PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));

    return eventRepository.findMistakes(userId, since, page);
  }
}

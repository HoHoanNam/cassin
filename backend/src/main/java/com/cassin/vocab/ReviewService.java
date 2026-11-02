package com.cassin.vocab;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.BoxCount;
import com.cassin.vocab.dto.ReviewCardResponse;
import com.cassin.vocab.dto.ReviewResultResponse;
import com.cassin.vocab.dto.ReviewStatsResponse;

import lombok.RequiredArgsConstructor;

/**
 * Ôn tập theo hộp Leitner. [V10] Đơn vị ôn là NGHĨA (Sense), không còn là thẻ.
 *
 * Hàng đợi có 2 phạm vi:
 *   deckId != null : các nghĩa thuộc từ trong một bộ
 *   deckId == null : "ôn tất cả" — toàn bộ sổ từ (từ của mình + từ hệ thống đang học / trong bộ của mình)
 */
@Service
@RequiredArgsConstructor
public class ReviewService {
  // Tối đa 100 thẻ đến hạn mỗi lần ôn.
  private static final int MAX_DUE_PER_QUEUE = 100;
  // Tối đa 50 thẻ mới mỗi lần ôn.
  private static final int MAX_NEW_PER_QUEUE = 50;
  // Chỉ đọc 400 ngày học gần nhất để tính chuỗi ngày liên tiếp.
  static final int MAX_STREAK_DAYS = 400;

  private final CardProgressRepository progressRepository;
  private final SenseRepository senseRepository;
  private final UserRepository userRepository;
  private final DeckService deckService;
  private final WordService wordService;
  private final StudyDayRepository studyDayRepository;
  private final Clock clock;

  /**
   * GET /api/review-queue?deckId=&newLimit=
   * Thẻ đến hạn đứng trước (sắp quên, ưu tiên hơn), sau đó tối đa newLimit thẻ mới.
   * [V10] Gộp 2 endpoint cũ (/decks/{id}/review-queue và /review-queue) làm một.
   */
  @Transactional(readOnly = true)
  public List<ReviewCardResponse> getQueue(Long deckId, int newLimit, Long userId) {
    if (deckId != null) {
      deckService.findReadable(deckId, userId);   // bộ của người khác → 404
    }
    LocalDate today = LocalDate.now(clock);
    PageRequest dueLimit = PageRequest.of(0, MAX_DUE_PER_QUEUE);

    List<CardProgress> due = deckId != null
        ? progressRepository.findDueInDeck(userId, deckId, today, dueLimit)
        : progressRepository.findAllDue(userId, today, dueLimit);
    List<ReviewCardResponse> queue = new ArrayList<>();
    for (CardProgress progress : due) {
      queue.add(ReviewCardResponse.due(progress));
    }

    // Math.clamp: ép newLimit vào khoảng [0, 50]. newLimit = 0 → chỉ ôn thẻ cũ.
    int limit = Math.clamp(newLimit, 0, MAX_NEW_PER_QUEUE);
    if (limit > 0) {
      PageRequest newPage = PageRequest.of(0, limit);
      List<Sense> fresh = deckId != null
          ? senseRepository.findNewInDeck(userId, deckId, newPage)
          : senseRepository.findNewInNotebook(userId, newPage);
      for (Sense sense : fresh) {
        queue.add(ReviewCardResponse.fresh(sense));
      }
    }
    return queue;
  }

  /** POST /api/senses/{senseId}/review */
  @Transactional
  public ReviewResultResponse review(Long senseId, boolean remembered, Long userId) {
    // [V10] Quyền nằm ở TỪ: ôn được nghĩa của từ mình hoặc từ hệ thống (không cần đi qua bộ).
    Sense sense = wordService.findReadableSense(senseId, userId);
    LocalDate today = LocalDate.now(clock);

    // Lần ôn đầu tiên: chưa có tiến độ → tạo mới (chưa lưu).
    CardProgress progress = progressRepository.findByUserIdAndSenseId(userId, senseId)
        .orElseGet(() -> CardProgress.start(userRepository.getReferenceById(userId), sense, today));

    try {
      // Toàn bộ luật Leitner nằm trong entity, service không tự tính hộp.
      progress.review(remembered, today);
    } catch (IllegalStateException ex) {
      // "đã ôn hôm nay" / "chưa đến hạn" → lỗi do người dùng → 409 kèm đúng câu đó.
      throw new BusinessException(HttpStatus.CONFLICT, "REVIEW_NOT_ALLOWED", ex.getMessage());
    }

    // Tiến độ mới → INSERT. Tiến độ cũ → dirty checking UPDATE cuối transaction.
    CardProgress saved = progressRepository.save(progress);
    // Chỉ đánh dấu "hôm nay có học" khi lượt ôn hợp lệ và đã lưu.
    studyDayRepository.markStudied(userId, today);
    return ReviewResultResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public ReviewStatsResponse getStats(Long userId) {
    LocalDate today = LocalDate.now(clock);
    // Cùng điều kiện lọc với findAllDue: số "đến hạn hôm nay" khớp đúng hàng đợi "ôn tất cả".
    long dueToday = progressRepository.countAllDue(userId, today);

    // DB chỉ trả các hộp CÓ thẻ. Biểu đồ cần đủ 5 cột → điền 0 cho hộp trống.
    Map<Integer, Long> byBox = new HashMap<>();
    for (BoxCount count : progressRepository.countByBox(userId)) {
      byBox.put(count.box(), count.total());
    }
    List<BoxCount> boxes = IntStream.rangeClosed(CardProgress.MIN_BOX, CardProgress.MAX_BOX)
        .mapToObj(box -> new BoxCount(box, byBox.getOrDefault(box, 0L)))
        .toList();

    int streakDays = streak(studyDayRepository.recentDays(userId, PageRequest.of(0, MAX_STREAK_DAYS)), today);
    return new ReviewStatsResponse(dueToday, boxes, streakDays);
  }

  /**
   * Đếm lùi từng ngày liên tiếp có học.
   * Bắt đầu từ HÔM NAY nếu hôm nay đã học, ngược lại từ HÔM QUA:
   * sáng nay chưa kịp ôn thì chuỗi vẫn còn, chỉ mất khi bỏ trọn một ngày.
   */
  static int streak(List<LocalDate> days, LocalDate today) {
    // HashSet để contains() chạy O(1), và không phụ thuộc thứ tự của days.
    Set<LocalDate> set = new HashSet<>(days);
    LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
    int count = 0;
    while (set.contains(cursor)) {
      count++;
      cursor = cursor.minusDays(1);
    }
    return count;
  }
}

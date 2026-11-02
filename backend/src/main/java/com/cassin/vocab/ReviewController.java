package com.cassin.vocab;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.vocab.dto.ReviewCardResponse;
import com.cassin.vocab.dto.ReviewRequest;
import com.cassin.vocab.dto.ReviewResultResponse;
import com.cassin.vocab.dto.ReviewStatsResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
  private final ReviewService reviewService;

  /**
   * [V10] Một endpoint cho cả 2 phạm vi:
   *   GET /api/review-queue?newLimit=10            → ôn tất cả từ trong sổ
   *   GET /api/review-queue?deckId=5&newLimit=10   → ôn một bộ
   * Thay cho GET /api/decks/{deckId}/review-queue (đã bỏ).
   */
  @GetMapping("/review-queue")
  public List<ReviewCardResponse> queue(
      @RequestParam(required = false) Long deckId,
      @RequestParam(defaultValue = "10") int newLimit,
      @AuthenticationPrincipal AuthUser user) {
    return reviewService.getQueue(deckId, newLimit, user.id());
  }

  /**
   * [V10] Chấm theo NGHĨA. Thay cho POST /api/cards/{cardId}/review.
   * POST (không phải PUT): mỗi lần chấm là một HÀNH ĐỘNG mới, gửi lại sẽ cho kết quả khác.
   */
  @PostMapping("/senses/{senseId}/review")
  public ReviewResultResponse review(
      @PathVariable Long senseId,
      @Valid @RequestBody ReviewRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return reviewService.review(senseId, request.remembered(), user.id());
  }

  @GetMapping("/review/stats")
  public ReviewStatsResponse stats(@AuthenticationPrincipal AuthUser user) {
    return reviewService.getStats(user.id());
  }
}

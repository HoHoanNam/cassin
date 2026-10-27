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
// [SỬA] Bỏ 2 dòng import GetMapping, RequestParam bị thừa (IDE tự thêm):
// "org.springframework.web.bind.annotation.*" ở trên đã gồm cả hai.

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
  private final ReviewService reviewService;

  @GetMapping("/decks/{deckId}/review-queue")
  public List<ReviewCardResponse> queue(
    @PathVariable Long deckId,
    @RequestParam(defaultValue = "10") int newLimit,
    @AuthenticationPrincipal AuthUser user
  ) {
    return reviewService.getQueue(deckId, newLimit, user.id());
  }

  // POST (không phải PUT): mỗi lần chấm là một HÀNH ĐỘNG mới, gửi lại sẽ cho kết quả khác.
  @PostMapping("/cards/{cardId}/review")
  public ReviewResultResponse review(
    @PathVariable Long cardId,
    @Valid @RequestBody ReviewRequest request,
    @AuthenticationPrincipal AuthUser user
  ) {
    return reviewService.review(cardId, request.remembered(), user.id());
  }

  @GetMapping("/review/stats")
  public ReviewStatsResponse stats(@AuthenticationPrincipal AuthUser user) {
    return reviewService.getStats(user.id());
  }
}


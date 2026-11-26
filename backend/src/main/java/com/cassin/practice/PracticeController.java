package com.cassin.practice;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.practice.dto.FillBlankQuestion;
import com.cassin.practice.dto.PracticeAnswer;
import com.cassin.practice.dto.PracticeAvailability;
import com.cassin.practice.dto.PracticeResult;
import com.cassin.practice.dto.PracticeStatsResponse;
import com.cassin.practice.dto.SentenceBuildQuestion;
import com.cassin.vocab.PracticeSource;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Luyện tập theo câu ví dụ, và thống kê kết quả luyện tập.
 *   - fill-blank / sentence-build: nguồn câu hỏi gộp được nhiều bộ (PracticeSource). Không chọn gì = sổ từ.
 *     Danh sách câu hỏi có thể ít hơn count (không đủ câu ví dụ phù hợp), hoặc rỗng.
 *   - stats: PracticeStatsService (tách service riêng để PracticeService chỉ lo tạo câu hỏi + chấm).
 */
@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {
  private final PracticeService practiceService;
  private final PracticeStatsService statsService;

  /**
   * [SỬA] deckId (1 bộ) → nguồn gộp nhiều bộ:
   *   ?all=true                    → "Tất cả": mọi bộ hệ thống + sổ từ
   *   ?deckIds=3&deckIds=7         → các bộ đã chọn (Spring nhận cả dạng deckIds=3,7)
   *   ?deckIds=3&notebook=true     → bộ 3 + sổ từ
   *   (không tham số)              → sổ từ
   */
  @GetMapping("/fill-blank")
  public List<FillBlankQuestion> fillBlank(
      @RequestParam(required = false) List<Long> deckIds,
      @RequestParam(defaultValue = "false") boolean notebook,
      @RequestParam(defaultValue = "false") boolean all,
      @RequestParam(defaultValue = "10") int count,
      @AuthenticationPrincipal AuthUser user) {
    return practiceService.fillBlank(new PracticeSource(all, notebook, deckIds), count, user.id());
  }

  @GetMapping("/sentence-build")
  public List<SentenceBuildQuestion> sentenceBuild(
      @RequestParam(required = false) List<Long> deckIds,
      @RequestParam(defaultValue = "false") boolean notebook,
      @RequestParam(defaultValue = "false") boolean all,
      @RequestParam(defaultValue = "10") int count,
      @AuthenticationPrincipal AuthUser user) {
    return practiceService.sentenceBuild(new PracticeSource(all, notebook, deckIds), count, user.id());
  }

  /**
   * [THÊM] Số câu tối đa mỗi chế độ cho nguồn đã chọn, cùng tham số nguồn với 2 API trên.
   *   GET /api/practice/availability?all=true → {"fillBlank": 312, "sentenceBuild": 290, "maxQuestions": 200}
   */
  @GetMapping("/availability")
  public PracticeAvailability availability(
      @RequestParam(required = false) List<Long> deckIds,
      @RequestParam(defaultValue = "false") boolean notebook,
      @RequestParam(defaultValue = "false") boolean all,
      @AuthenticationPrincipal AuthUser user) {
    return practiceService.availability(new PracticeSource(all, notebook, deckIds), user.id());
  }

  @PostMapping("/answers")
  public PracticeResult answer(@Valid @RequestBody PracticeAnswer answer, @AuthenticationPrincipal AuthUser user) {
    return practiceService.check(answer, user.id());
  }

  /**
   * Thống kê luyện tập N ngày gần nhất (theo lịch Việt Nam, gồm hôm nay).
   *   GET /api/practice/stats          → 7 ngày
   *   GET /api/practice/stats?days=30  → 30 ngày
   */
  @GetMapping("/stats")
  public PracticeStatsResponse stats(
      @RequestParam(defaultValue = "7") int days,
      @AuthenticationPrincipal AuthUser user) {
    return statsService.stats(user.id(), days);
  }
}

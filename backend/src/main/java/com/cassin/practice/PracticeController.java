package com.cassin.practice;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.practice.dto.FillBlankQuestion;
import com.cassin.practice.dto.PracticeAnswer;
import com.cassin.practice.dto.PracticeResult;
import com.cassin.practice.dto.SentenceBuildQuestion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Luyện tập theo câu ví dụ. deckId bỏ trống = luyện trên toàn bộ sổ từ.
 * Danh sách trả về có thể ít hơn count (không đủ câu ví dụ phù hợp), hoặc rỗng.
 */
@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {
  private final PracticeService practiceService;

  @GetMapping("/fill-blank")
  public List<FillBlankQuestion> fillBlank(
      @RequestParam(required = false) Long deckId,
      @RequestParam(defaultValue = "10") int count,
      @AuthenticationPrincipal AuthUser user) {
    return practiceService.fillBlank(deckId, count, user.id());
  }

  @GetMapping("/sentence-build")
  public List<SentenceBuildQuestion> sentenceBuild(
      @RequestParam(required = false) Long deckId,
      @RequestParam(defaultValue = "10") int count,
      @AuthenticationPrincipal AuthUser user) {
    return practiceService.sentenceBuild(deckId, count, user.id());
  }

  @PostMapping("/answers")
  public PracticeResult answer(@Valid @RequestBody PracticeAnswer answer, @AuthenticationPrincipal AuthUser user) {
    return practiceService.check(answer, user.id());
  }
}

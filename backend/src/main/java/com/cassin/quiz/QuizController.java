package com.cassin.quiz;

// [SỬA] Bỏ 2 import không dùng: java.net.http.HttpResponse (IDE tự thêm nhầm, là class của
// HTTP client Java, không liên quan Spring) và java.util.List (history trả PageResponse, không trả List).

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.quiz.dto.QuizHistoryItem;
import com.cassin.quiz.dto.QuizResultResponse;
import com.cassin.quiz.dto.QuizStartResponse;
import com.cassin.quiz.dto.SubmitQuizRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping("/api")
@RequiredArgsConstructor 
public class QuizController {
  private final QuizService quizService;

  // 201: tạo ra một lượt làm bài mới
  @PostMapping("/grammar/topics/{topicId}/quiz-attempts") 
  @ResponseStatus(HttpStatus.CREATED)
  public QuizStartResponse start(
    @PathVariable Long topicId,
    @AuthenticationPrincipal AuthUser user
  ) {
    return quizService.start(topicId, user.id());
  }

  // [SỬA] Thêm "/" đầu cho thống nhất với 2 endpoint còn lại ("/grammar/...", "/quiz-attempts").
  // Spring vẫn tự nối thành /api/quiz-attempts/... nên trước đây không lỗi, nhưng viết đủ dễ đọc hơn.
  @PostMapping("/quiz-attempts/{attemptId}/submit")
  public QuizResultResponse submit(
    @PathVariable Long attemptId,
    @Valid @RequestBody SubmitQuizRequest request,
    @AuthenticationPrincipal AuthUser user
  ) {
    return quizService.submit(attemptId, request, user.id());
  }

  @GetMapping("/quiz-attempts/{attemptId}/result")
  public QuizResultResponse result(
    @PathVariable Long attemptId, 
    @AuthenticationPrincipal AuthUser user
  ) {
    return quizService.getResult(attemptId, user.id());
  } 

  @GetMapping("/quiz-attempts")
  public PageResponse<QuizHistoryItem> history(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @AuthenticationPrincipal AuthUser user
  ) {
    return quizService.history(page, size, user.id());
  }
}

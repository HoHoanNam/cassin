package com.cassin.passage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.grammar.CefrLevel;
import com.cassin.passage.dto.AttemptResponse;
import com.cassin.passage.dto.AttemptResultResponse;
import com.cassin.passage.dto.HintResponse;
import com.cassin.passage.dto.PassageSummary;
import com.cassin.passage.dto.SubmitRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Bài đọc, phía học viên. Hai nhóm đường dẫn:
 *   /api/passages          : danh sách bài và "Làm bài"
 *   /api/reading-attempts  : một lượt làm (tên theo bảng reading_attempts)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PassageController {
  private final PassageService passageService;

  /** GET /api/passages?level=B1&page=0&size=20 (level bỏ trống = mọi trình độ) */
  @GetMapping("/passages")
  public PageResponse<PassageSummary> list(
      @RequestParam(required = false) CefrLevel level,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return passageService.list(level, page, size, user.id());
  }

  /** 201 khi tạo lượt mới, 200 khi trả lại lượt đang làm dở → cần ResponseEntity để chọn mã theo từng lần gọi. */
  @PostMapping("/passages/{passageId}/attempts")
  public ResponseEntity<AttemptResponse> start(@PathVariable Long passageId, @AuthenticationPrincipal AuthUser user) {
    StartedAttempt started = passageService.start(passageId, user.id());
    return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK).body(started.attempt());
  }

  @GetMapping("/reading-attempts/{attemptId}")
  public AttemptResponse get(@PathVariable Long attemptId, @AuthenticationPrincipal AuthUser user) {
    return passageService.getAttempt(attemptId, user.id());
  }

  @PostMapping("/reading-attempts/{attemptId}/questions/{questionId}/hint")
  public HintResponse hint(@PathVariable Long attemptId, @PathVariable Long questionId,
      @AuthenticationPrincipal AuthUser user) {
    return passageService.revealHint(attemptId, questionId, user.id());
  }

  @PostMapping("/reading-attempts/{attemptId}/submit")
  public AttemptResultResponse submit(@PathVariable Long attemptId, @Valid @RequestBody SubmitRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return passageService.submit(attemptId, request, user.id());
  }

  @GetMapping("/reading-attempts/{attemptId}/result")
  public AttemptResultResponse result(@PathVariable Long attemptId, @AuthenticationPrincipal AuthUser user) {
    return passageService.result(attemptId, user.id());
  }
}

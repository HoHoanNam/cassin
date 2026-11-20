package com.cassin.passage;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.passage.dto.AdminPassageResponse;
import com.cassin.passage.dto.AdminPassageSummary;
import com.cassin.passage.dto.PassageRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Soạn bài đọc. /api/admin/** đã được SecurityConfig giới hạn cho ROLE_ADMIN, không cần annotation thêm. */
@RestController
@RequestMapping("/api/admin/passages")
@RequiredArgsConstructor
public class AdminPassageController {
  private final AdminPassageService adminPassageService;

  /** GET /api/admin/passages?status=DRAFT&page=0&size=20 (status bỏ trống = tất cả) */
  @GetMapping
  public PageResponse<AdminPassageSummary> list(
      @RequestParam(required = false) PassageStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return adminPassageService.list(status, page, size);
  }

  @GetMapping("/{passageId}")
  public AdminPassageResponse get(@PathVariable Long passageId) {
    return adminPassageService.get(passageId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminPassageResponse create(@Valid @RequestBody PassageRequest request,
      @AuthenticationPrincipal AuthUser admin) {
    return adminPassageService.create(request, admin.id());
  }

  /** Thay toàn bộ nội dung + câu hỏi. 409 PASSAGE_LOCKED nếu đã có người làm. */
  @PutMapping("/{passageId}")
  public AdminPassageResponse update(@PathVariable Long passageId, @Valid @RequestBody PassageRequest request) {
    return adminPassageService.update(passageId, request);
  }

  @PatchMapping("/{passageId}/publish")
  public AdminPassageResponse publish(@PathVariable Long passageId) {
    return adminPassageService.publish(passageId);
  }

  @PatchMapping("/{passageId}/archive")
  public AdminPassageResponse archive(@PathVariable Long passageId) {
    return adminPassageService.archive(passageId);
  }

  @DeleteMapping("/{passageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long passageId) {
    adminPassageService.delete(passageId);
  }
}

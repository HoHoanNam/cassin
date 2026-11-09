package com.cassin.report;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.report.dto.CreateReportRequest;
import com.cassin.report.dto.ReportResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Báo lỗi nội dung, phía học viên. Phía admin: /api/admin/reports (AdminReportController, chưa làm). */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
  private final ReportService reportService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReportResponse create(@Valid @RequestBody CreateReportRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return reportService.create(request, user.id());
  }

  @GetMapping
  public PageResponse<ReportResponse> list(
      @RequestParam(defaultValue = "0") int page,
      // [SỬA] 50 → 20: mặc định nên là cỡ trang bình thường; 50 là mức TỐI ĐA (MAX_PAGE_SIZE) client được xin.
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return reportService.listMine(page, size, user.id());
  }

  // [SỬA] Thêm @ResponseStatus(NO_CONTENT). Method void không có annotation này trả 200 với body rỗng;
  // xóa thành công theo quy ước của cả dự án là 204 (giống DeckController.delete).
  @DeleteMapping("/{reportId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void withdraw(@PathVariable Long reportId, @AuthenticationPrincipal AuthUser user) {
    reportService.withdraw(reportId, user.id());
  }
}

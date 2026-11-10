package com.cassin.scan;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.scan.dto.ScanRequest;
import com.cassin.scan.dto.ScanResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Quét đoạn văn tìm từ chưa học. Chỉ ĐỌC dữ liệu, không lưu gì:
 * thêm từ vào bộ dùng lại POST /api/decks/{deckId}/words/{wordId} và POST /api/words.
 */
@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {
  private final TextScanService scanService;

  /**
   * POST /api/scan   body: { "text": "..." }   → 200 ScanResult
   *
   * POST chứ không phải GET ?text=...: đoạn tới 5.000 ký tự vượt giới hạn độ dài URL của nhiều server / proxy,
   * và URL bị ghi vào log, lịch sử trình duyệt. Không tạo tài nguyên mới nên trả 200, không phải 201.
   */
  @PostMapping
  public ScanResult scan(@Valid @RequestBody ScanRequest request, @AuthenticationPrincipal AuthUser user) {
    return scanService.scan(request.text(), user.id());
  }
}
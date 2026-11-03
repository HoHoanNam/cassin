package com.cassin.practice;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cassin.auth.AuthUser;
import com.cassin.practice.dto.MistakeItem;

import lombok.RequiredArgsConstructor;

/**
 * Sổ lỗi sai của người đang đăng nhập.
*   GET /api/mistakes                        → 7 ngày gần đây, tối đa 20 nghĩa
 *   GET /api/mistakes?days=30&limit=50      → 30 ngày gần đây và tối đa 50 nghĩa
 */
@RestController
@RequestMapping("/api/mistakes")
@RequiredArgsConstructor
public class MistakeController {
  private final MistakeService mistakeService;

  @GetMapping
  public List<MistakeItem> list(
      @RequestParam(defaultValue = "7") int days,
      @RequestParam(defaultValue = "20") int limit,
      @AuthenticationPrincipal AuthUser user) {
    return mistakeService.list(user.id(), days, limit);
  }
}
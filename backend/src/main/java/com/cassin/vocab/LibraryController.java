package com.cassin.vocab;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.vocab.dto.LibraryWord;

import lombok.RequiredArgsConstructor;

/**
 * Thư viện từ hệ thống.
 *   GET /api/library?q=light                → tìm mọi từ hệ thống
 *   GET /api/library?onlyNew=true           → chỉ từ chưa có trong sổ từ của tôi
 */
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {
  private final LibraryService libraryService;

  @GetMapping
  public PageResponse<LibraryWord> search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "false") boolean onlyNew,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return libraryService.search(user.id(), q, onlyNew, page, size);
  }
}
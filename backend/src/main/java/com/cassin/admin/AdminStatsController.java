package com.cassin.admin;

import org.springframework.web.bind.annotation.*;

import com.cassin.admin.dto.AdminStatsResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {
  private final AdminStatsService adminStatsService;

  @GetMapping("/stats")
  public AdminStatsResponse stats() {
    return adminStatsService.getStats();
  }
}

package com.cassin.practice.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/practice/stats
 *
 * @param accuracy tỉ lệ đúng từ 0.0 đến 1.0 (0 nếu chưa luyện câu nào). Frontend nhân 100 để hiện %.
 * @param daily    ĐỦ mọi ngày trong khoảng, kể cả ngày không luyện (total = 0)
 */
public record PracticeStatsResponse(
    long total,
    long correct,
    double accuracy,
    List<ModeStat> byMode,
    List<DayStat> daily) {
  public record DayStat(LocalDate date, long total, long correct) {
  }
}
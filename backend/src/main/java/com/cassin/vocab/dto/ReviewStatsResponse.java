package com.cassin.vocab.dto;

import java.util.List;

/**
 * Tổng hợp kết quả ôn: số thẻ đến hạn (cần ôn ngay) + số thẻ ở mỗi hộp.
 */
public record ReviewStatsResponse(long dueToday, List<BoxCount> boxes, int streakDays) {}

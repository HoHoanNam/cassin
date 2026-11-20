package com.cassin.passage.dto;

import java.time.Instant;

import com.cassin.grammar.CefrLevel;

/** Một thẻ trong danh sách bài đọc của học viên. bestScore = null nếu chưa nộp lần nào. */
public record PassageSummary(
    Long id,
    String title,
    CefrLevel level,
    int wordCount,
    int readingMinutes,
    int questionCount,
    Integer bestScore,
    Instant publishedAt) {
}

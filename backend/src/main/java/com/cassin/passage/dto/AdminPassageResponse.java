package com.cassin.passage.dto;

import java.time.Instant;
import java.util.List;

import com.cassin.grammar.CefrLevel;
import com.cassin.passage.PassageStatus;

/** @param locked true khi đã có người làm → FE khóa phần câu hỏi, chỉ cho lưu trữ bài. */
public record AdminPassageResponse(
    Long id,
    String title,
    String content,
    CefrLevel level,
    PassageStatus status,
    int wordCount,
    boolean locked,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt,
    List<AdminQuestionResponse> questions) {
}

package com.cassin.passage.dto;

import java.time.Instant;

import com.cassin.grammar.CefrLevel;
import com.cassin.passage.PassageStatus;

public record AdminPassageSummary(
    Long id,
    String title,
    CefrLevel level,
    PassageStatus status,
    int wordCount,
    int questionCount,
    Instant updatedAt,
    Instant publishedAt) {
}

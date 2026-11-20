package com.cassin.passage.dto;

import java.time.Instant;
import java.util.List;

/** "8/10 đúng · 5 câu đúng không cần gợi ý" + chi tiết từng câu. */
public record AttemptResultResponse(
    Long attemptId,
    PassageView passage,
    int score,
    int total,
    long correctWithoutHints,
    Instant startedAt,
    Instant submittedAt,
    List<QuestionResult> questions) {
}

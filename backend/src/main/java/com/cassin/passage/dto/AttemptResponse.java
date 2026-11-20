package com.cassin.passage.dto;

import java.time.Instant;
import java.util.List;

/** Đề của một lượt làm (bắt đầu mới hoặc làm tiếp). */
public record AttemptResponse(Long attemptId, PassageView passage, List<QuestionView> questions, Instant startedAt) {
}

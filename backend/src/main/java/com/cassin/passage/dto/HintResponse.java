package com.cassin.passage.dto;

/** Trả về khi học viên bấm "Gợi ý". */
public record HintResponse(Long questionId, HintView hint, int hintsRemaining) {
}

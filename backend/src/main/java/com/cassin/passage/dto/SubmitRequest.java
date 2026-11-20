package com.cassin.passage.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Câu nào không có trong danh sách = bỏ trống. */
public record SubmitRequest(@NotNull List<@Valid @NotNull AnswerRequest> answers) {
}

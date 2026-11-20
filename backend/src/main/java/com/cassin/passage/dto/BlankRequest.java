package com.cassin.passage.dto;

import java.util.List;

import com.cassin.passage.AnswerKind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** maxWords bỏ trống khi answerKind = NUMBER. Luật đầy đủ (đáp án chuẩn phải đúng giới hạn...) nằm ở BlankQuestion. */
public record BlankRequest(
    @NotBlank(message = "Vui lòng nhập câu có chỗ trống ____") String prompt,
    @NotBlank(message = "Vui lòng chọn câu bằng chứng trong bài") String evidence,
    @NotBlank(message = "Vui lòng nhập lời giải thích") String explanation,
    @Size(max = 3, message = "Mỗi câu tối đa 3 gợi ý") List<@Valid @NotNull HintRequest> hints,
    @NotNull(message = "Vui lòng chọn loại đáp án") AnswerKind answerKind,
    Integer maxWords,
    @NotEmpty(message = "Cần ít nhất 1 đáp án") @Size(max = 5, message = "Tối đa 5 đáp án được chấp nhận")
    List<@NotBlank @Size(max = 100) String> acceptedAnswers) implements QuestionRequest {
}

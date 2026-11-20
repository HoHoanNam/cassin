package com.cassin.passage.dto;

import java.util.List;

import com.cassin.passage.HintInput;
import com.cassin.passage.QuestionContent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Câu hỏi admin gửi lên. Field "type" cho Jackson biết tạo record nào:
 *   { "type": "TFNG", "prompt": "...", "answer": "NOT_GIVEN", ... }
 *
 * sealed + permits: chỉ đúng 3 dạng → switch (request) trong PassageMapper không cần nhánh default,
 * và thêm dạng thứ 4 mà quên xử lý thì compiler báo lỗi.
 * (DTO là record thường, không bị Hibernate tạo proxy, nên dùng sealed được — khác với entity.)
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = McqRequest.class, name = "MCQ"),
    @JsonSubTypes.Type(value = TfngRequest.class, name = "TFNG"),
    @JsonSubTypes.Type(value = BlankRequest.class, name = "BLANK")
})
public sealed interface QuestionRequest permits McqRequest, TfngRequest, BlankRequest {

  String prompt();

  String evidence();

  String explanation();

  List<HintRequest> hints();

  /** Phần chung → record domain. */
  default QuestionContent toContent() {
    List<HintInput> inputs = hints() == null ? List.of() : hints().stream().map(HintRequest::toInput).toList();
    return new QuestionContent(prompt(), evidence(), explanation(), inputs);
  }
}

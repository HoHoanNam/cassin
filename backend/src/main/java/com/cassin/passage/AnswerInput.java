package com.cassin.passage;

/**
 * Câu trả lời cho MỘT câu hỏi. Mỗi dạng chỉ đọc một field:
 *   MCQ → optionId · TFNG → tfng · BLANK → text. Cả ba null = bỏ trống.
 */
public record AnswerInput(Long optionId, TfngAnswer tfng, String text) {

  public static AnswerInput empty() {
    return new AnswerInput(null, null, null);
  }
}

package com.cassin.passage;

/**
 * Kết quả chấm một câu.
 *
 * @param rejectedReason chỉ BLANK: sai định dạng (quá số từ...)
 * @param selectedOption chỉ MCQ: lựa chọn đã chọn, để ReadingAnswer lưu khóa ngoại
 */
public record GradeResult(boolean correct, RejectedReason rejectedReason, ReadingOption selectedOption) {

  public static GradeResult blank() {
    return new GradeResult(false, null, null);
  }

  public static GradeResult of(boolean correct) {
    return new GradeResult(correct, null, null);
  }

  public static GradeResult rejected(RejectedReason reason) {
    return new GradeResult(false, reason, null);
  }

  public static GradeResult option(ReadingOption option) {
    return new GradeResult(option.isCorrect(), null, option);
  }
}

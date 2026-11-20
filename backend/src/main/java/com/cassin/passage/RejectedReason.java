package com.cassin.passage;

/** Câu điền từ bị chấm sai vì SAI ĐỊNH DẠNG (chưa cần so đáp án). FE dùng để giải thích. */
public enum RejectedReason {
  OVER_WORD_LIMIT,    // quá số từ cho phép
  NUMBER_REQUIRED,    // đề yêu cầu một số
  NUMBER_NOT_ALLOWED, // đề chỉ nhận chữ
  TOO_MANY_NUMBERS    // nhiều hơn một số
}

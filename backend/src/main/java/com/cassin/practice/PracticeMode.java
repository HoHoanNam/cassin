package com.cassin.practice;

/**
 * Các chế độ luyện. Lưu dạng chữ trong review_events.mode (CHECK trong V10 phải khớp danh sách này).
 *
 *   FLASHCARD      : lật thẻ nhớ / quên (chấm Leitner ở ReviewService, không qua PracticeService)
 *   FILL_BLANK     : điền từ còn thiếu vào câu ví dụ
 *   SENTENCE_BUILD : xếp các mảnh thành câu ví dụ đúng thứ tự
 */
public enum PracticeMode {
  FLASHCARD,
  FILL_BLANK,
  SENTENCE_BUILD
}

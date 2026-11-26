package com.cassin.practice.dto;

/**
 * [THÊM] GET /api/practice/availability: số câu tối đa có thể hỏi cho nguồn đã chọn.
 *
 * @param fillBlank     số nghĩa có câu đánh dấu [từ đích] → tối đa bấy nhiêu câu Điền từ
 * @param sentenceBuild số nghĩa có câu dài 5–15 mảnh → tối đa bấy nhiêu câu Xếp câu
 * @param maxQuestions  trần mỗi lượt của server (MAX trên FE = min(có sẵn, trần))
 */
public record PracticeAvailability(int fillBlank, int sentenceBuild, int maxQuestions) {
}

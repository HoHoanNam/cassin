package com.cassin.grammar;

/**
 * Dữ liệu đầu vào của MỘT đáp án, dùng khi tạo / sửa câu hỏi.
 * Question tự tạo các QuestionOption từ đây, nên bên ngoài không bao giờ tạo QuestionOption lẻ.
 *
 * [SỬA] Bỏ field "position": vị trí lấy theo thứ tự trong danh sách (phần tử đầu là vị trí 0...).
 *   Nếu để người gọi tự truyền, có thể truyền trùng (0, 0, 1, 2) hoặc nhảy cóc (0, 1, 2, 7).
 * [SỬA] Bỏ method of(...): record đã có sẵn constructor new OptionInput(...), không cần thêm.
 * [SỬA] Đổi isCorrect → correct. Record tự sinh method trùng tên field, nên sẽ gọi là input.correct().
 */
public record OptionInput(String content, boolean correct) {
}

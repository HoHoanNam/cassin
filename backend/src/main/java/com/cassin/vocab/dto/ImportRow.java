package com.cassin.vocab.dto;

import java.util.List;

/**
 * Một dòng dữ liệu trong file CSV, CHƯA kiểm tra: mọi ô vẫn là chuỗi thô (đã cắt khoảng trắng).
 *
 * Vì sao chưa đổi partOfSpeech sang enum ngay? Đọc file và kiểm tra là 2 bước riêng: bước đọc
 * chỉ lo "tách đúng ô", bước kiểm tra mới quyết định ô nào sai. Như vậy một dòng sai loại từ
 * vẫn đọc được, và được báo lỗi CÙNG LÚC với các dòng sai khác (thay vì dừng ở lỗi đầu tiên).
 *
 * @param line số dòng trong file mà người dùng nhìn thấy (dòng tiêu đề là 1) → "Dòng 7: thiếu nghĩa"
 */
public record ImportRow(
    long line,
    String term,
    String phonetic,
    String partOfSpeech,
    String definition,
    List<String> examples) {
}

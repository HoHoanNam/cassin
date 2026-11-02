package com.cassin.vocab;

import java.util.List;

/**
 * Dữ liệu đầu vào của MỘT nghĩa, dùng khi tạo / sửa từ (giống OptionInput bên grammar).
 * Word.replaceSenses(...) nhận danh sách này và tự tạo / sửa / xóa các Sense.
 *
 * @param id           null = nghĩa MỚI; có id = sửa nghĩa đang có (giữ nguyên id → giữ tiến độ ôn)
 * @param partOfSpeech loại từ của nghĩa này
 * @param definition   nghĩa tiếng Việt
 * @param examples     câu ví dụ; có thể đánh dấu từ đích bằng [ ], vd "Her English has [improved] a lot."
 */
public record SenseInput(Long id, PartOfSpeech partOfSpeech, String definition, List<String> examples) {
}

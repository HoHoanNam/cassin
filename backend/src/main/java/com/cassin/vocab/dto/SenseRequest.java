package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.PartOfSpeech;
import com.cassin.vocab.Sense;
import com.cassin.vocab.SenseInput;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Một nghĩa trong WordRequest.
 *
 * @param id null = thêm nghĩa mới; có id = sửa nghĩa đang có (giữ tiến độ ôn của nghĩa đó).
 *           Nghĩa cũ KHÔNG có trong danh sách gửi lên sẽ bị xóa.
 */
public record SenseRequest(
    Long id,

    @NotNull(message = "Vui lòng chọn loại từ")
    PartOfSpeech partOfSpeech,

    @NotBlank(message = "Vui lòng nhập nghĩa")
    @Size(max = 255, message = "Nghĩa tối đa 255 ký tự")
    String definition,

    // Kiểm tra cả danh sách (tối đa 5 câu) lẫn từng câu (tối đa 500 ký tự).
    // Định dạng đánh dấu [từ] do Sense / ExampleMarkup kiểm tra (annotation không diễn tả được).
    @Size(max = Sense.MAX_EXAMPLES, message = "Mỗi nghĩa tối đa 5 câu ví dụ")
    List<@Size(max = 500, message = "Mỗi câu ví dụ tối đa 500 ký tự") String> examples
) {
  /** DTO (có annotation web) → record domain (không phụ thuộc tầng web). */
  public SenseInput toInput() {
    return new SenseInput(id, partOfSpeech, definition, examples);
  }
}

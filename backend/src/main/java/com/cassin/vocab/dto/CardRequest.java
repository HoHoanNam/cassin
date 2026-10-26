package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.Card;
import com.cassin.vocab.PartOfSpeech;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CardRequest(
  // [SỬA] Thêm message cho các @Size. Không ghi message thì Hibernate Validator dùng câu mặc định
  // theo ngôn ngữ của máy chạy server (VD: "size must be between 0 and 100"),
  // và frontend sẽ hiện câu tiếng Anh lẫn giữa các lỗi tiếng Việt.
  @NotBlank(message = "Vui lòng nhập từ") 
  @Size(max = 100, message = "Từ tối đa 100 ký tự") 
  String word,

  @NotNull(message = "Vui lòng chọn loại từ") 
  PartOfSpeech partOfSpeech,

  @Size(max = 100, message = "Phiên âm tối đa 100 ký tự") 
  String phonetic,

  @NotBlank(message = "Vui lòng nhập nghĩa") 
  @Size(max = 255, message = "Nghĩa tối đa 255 ký tự") 
  String definition,

  // Kiểm tra cả list (tối đa 5 câu) lẫn từng phần tử (mỗi câu tối đa 500 ký tự)
  @Size(max = Card.MAX_EXAMPLES, message = "Tối đa 5 câu ví dụ") 
  // Lỗi ở từng câu sẽ có key dạng "examples[2]" trong fieldErrors → frontend biết câu thứ mấy bị sai.
  List<@Size(max = 500, message = "Mỗi câu ví dụ tối đa 500 ký tự") String> examples
) {}

package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.SenseInput;
import com.cassin.vocab.Word;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tạo / sửa một từ kèm toàn bộ nghĩa (POST /api/words, PUT /api/words/{id}, và bản admin).
 *
 * @param deckIds các bộ CÁ NHÂN muốn đặt từ này vào. null = không đổi (khi sửa) / không vào bộ nào (khi tạo).
 *                Danh sách rỗng [] = bỏ từ khỏi mọi bộ cá nhân. Bản admin bỏ qua field này.
 */
public record WordRequest(
    @NotBlank(message = "Vui lòng nhập từ")
    @Size(max = 100, message = "Từ tối đa 100 ký tự")
    String term,

    @Size(max = 100, message = "Phiên âm tối đa 100 ký tự")
    String phonetic,

    @NotEmpty(message = "Từ phải có ít nhất 1 nghĩa")
    @Size(max = Word.MAX_SENSES, message = "Mỗi từ tối đa 10 nghĩa")
    List<@Valid @NotNull SenseRequest> senses,

    List<@NotNull Long> deckIds
) {
  public List<SenseInput> senseInputs() {
    return senses.stream().map(s -> s.toInput()).toList();
  }
}

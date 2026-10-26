package com.cassin.vocab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeckRequest(
    @NotBlank(message = "Vui lòng nhập tên bộ") 
    @Size(max = 100, message = "Tên bộ tối đa 100 ký tự")
    String deckName,

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    String description
) {}

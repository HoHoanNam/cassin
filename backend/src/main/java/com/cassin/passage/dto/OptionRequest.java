package com.cassin.passage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OptionRequest(
    @NotBlank(message = "Lựa chọn không được để trống") @Size(max = 255) String content,
    boolean correct) {
}

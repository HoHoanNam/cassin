package com.cassin.grammar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OptionRequest(@NotBlank @Size(max = 255) String content, boolean correct) {
}

package com.cassin.scan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/scan */
public record ScanRequest(
    // khớp SCAN_MAX_LENGTH ở FE
    @NotBlank(message = "Hãy dán một đoạn tiếng Anh") @Size(max = 5000, message = "Đoạn văn tối đa 5.000 ký tự") String text) {
}

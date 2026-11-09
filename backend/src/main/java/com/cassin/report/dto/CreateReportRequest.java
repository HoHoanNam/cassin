package com.cassin.report.dto;

import com.cassin.report.ReportReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// [SỬA] Thêm message="..." tiếng Việt cho từng annotation, giống các DTO khác trong dự án.
// Không ghi message thì Hibernate Validator dùng câu mặc định ("must not be null") → FE hiện tiếng Anh dưới ô nhập.
// Chuỗi "{...}" bị Spring đọc thành fieldErrors: { "senseId": "Thiếu nghĩa cần báo", ... } (GlobalExceptionHandler).
public record CreateReportRequest(
    @NotNull(message = "Thiếu nghĩa cần báo") Long senseId,
    @NotNull(message = "Chọn lý do báo lỗi") ReportReason reason,
    @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String message) {
}

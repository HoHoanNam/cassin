package com.cassin.common.dto;

import java.util.Map;

// Mọi lỗi trả về cùng một dạng, giao diện chỉ cần xử lý một kiểu.
// Map lưu dữ liệu theo key - value, key là tên field, value là thông báo lỗi cụ thể.
public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {
}

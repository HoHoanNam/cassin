package com.cassin.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		// [SỬA] Thêm message cho @Size: thiếu thì trả "size must be between 0 and 100".
		@NotBlank(message = "Vui lòng nhập tên hiển thị") @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự") String displayName,
		@Size(max = 500, message = "Giới thiệu tối đa 500 ký tự") String bio) {
}

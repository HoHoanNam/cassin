package com.cassin.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
		// [SỬA] Thêm message cho @NotBlank: thiếu thì Spring trả câu mặc định tiếng Anh "must not be blank".
		// @Size đếm KÝ TỰ; giới hạn 72 BYTE của BCrypt do PasswordRules kiểm tra thêm ở UserService.
		@NotBlank(message = "Vui lòng nhập mật khẩu mới") @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự") String newPassword) {
}

package com.cassin.auth;

import com.cassin.user.Role;

/**
 * Người đang đăng nhập, dựng lại từ token ở mỗi request.
 * Không phải entity: không truy vấn database, chỉ chứa những gì cần để kiểm tra quyền.
 */
public record AuthUser(Long id, String email, Role role) {}

package com.cassin.auth.dto;

/** [THÊM] Kết quả "Đăng xuất mọi thiết bị khác": số thiết bị vừa bị đăng xuất. */
public record RevokedSessionsResponse(int revoked) {
}

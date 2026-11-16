-- Refresh token: phiên đăng nhập dài hạn, thu hồi được (xem backend/docs/auth.md).
--
-- Mỗi lần đăng nhập mở một "họ" token (family_id). Mỗi lần refresh: token cũ bị thu hồi,
-- token mới cùng họ được cấp (xoay vòng). Token đã thu hồi mà bị dùng lại = dấu hiệu bị đánh cắp
-- → thu hồi CẢ HỌ.
--
-- Chỉ lưu SHA-256 của token (64 ký tự hex), KHÔNG lưu token gốc: lộ bảng này cũng không dùng được.
-- Dòng đã thu hồi được GIỮ tới khi hết hạn (để còn nhận ra lần dùng lại), job dọn dẹp xóa sau đó.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    family_id   UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_expiry CHECK (expires_at > created_at)
);

-- Thu hồi cả họ (dùng lại token, đăng xuất).
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
-- Thu hồi mọi phiên của một người (đổi mật khẩu): chỉ cần các dòng CÒN hiệu lực.
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
-- Job dọn dẹp token hết hạn.
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

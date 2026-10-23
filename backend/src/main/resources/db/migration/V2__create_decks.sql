-- Bộ từ vựng: bộ hệ thống (ADMIN_DECK, không có chủ) hoặc bộ cá nhân (USER_DECK, có chủ).
CREATE TABLE decks (
    id          BIGSERIAL PRIMARY KEY,
    deck_name   VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    deck_type   VARCHAR(20)  NOT NULL,
    -- Cho phép NULL vì bộ hệ thống không có chủ.
    -- ON DELETE CASCADE: xóa người dùng thì xóa luôn các bộ cá nhân của họ.
    owner_id    BIGINT REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Lớp bảo vệ cuối ở database: dù code Java có bug, DB vẫn không cho lưu trạng thái vô lý.
    CONSTRAINT chk_decks_type_owner CHECK (
        (deck_type = 'USER_DECK'  AND owner_id IS NOT NULL) OR
        (deck_type = 'ADMIN_DECK' AND owner_id IS NULL)
    )
);

-- Truy vấn "các bộ của tôi" (WHERE owner_id = ?) chạy rất thường xuyên → cần index.
-- PostgreSQL KHÔNG tự tạo index cho khóa ngoại, phải tự thêm.
CREATE INDEX idx_decks_owner_id ON decks (owner_id);

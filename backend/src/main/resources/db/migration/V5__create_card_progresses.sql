-- Tiến độ ôn thẻ theo hộp Leitner, riêng cho từng người dùng.
CREATE TABLE card_progresses (
    id               BIGSERIAL PRIMARY KEY,
    -- Xóa người dùng hoặc xóa thẻ thì DB tự xóa tiến độ liên quan.
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    card_id          BIGINT      NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    -- CHECK thật cho box nằm ở đây (không phải columnDefinition trong Java).
    box              INTEGER     NOT NULL CHECK (box BETWEEN 1 AND 5),
    -- DATE (chỉ ngày) khớp với LocalDate trong Java.
    due_date         DATE        NOT NULL,
    last_reviewed_on DATE,
    review_count     INTEGER     NOT NULL DEFAULT 0 CHECK (review_count >= 0),
    lapse_count      INTEGER     NOT NULL DEFAULT 0 CHECK (lapse_count >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Mỗi người chỉ có MỘT tiến độ cho mỗi thẻ.
    -- Ràng buộc này cũng tạo sẵn index (user_id, card_id): tra "tiến độ của tôi với thẻ X" rất nhanh.
    CONSTRAINT uq_card_progresses_user_card UNIQUE (user_id, card_id)
);

-- Câu truy vấn chạy nhiều nhất: "thẻ đến hạn của tôi" (WHERE user_id = ? AND due_date <= ?).
CREATE INDEX idx_card_progresses_user_due ON card_progresses (user_id, due_date);

-- Index cho card_id: khi xóa một thẻ, DB phải tìm mọi dòng tiến độ có card_id đó để xóa theo.
-- Unique (user_id, card_id) KHÔNG giúp được, vì card_id đứng thứ hai trong index đó.
-- Không có index này, mỗi lần xóa thẻ là quét toàn bộ bảng card_progresses.
CREATE INDEX idx_card_progresses_card_id ON card_progresses (card_id);

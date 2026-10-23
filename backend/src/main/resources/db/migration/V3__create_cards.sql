-- Thẻ từ vựng: mỗi thẻ là MỘT nghĩa của MỘT từ.
-- Từ nhiều nghĩa / nhiều loại từ = nhiều dòng cùng "word" trong một bộ.
CREATE TABLE cards (
    id             BIGSERIAL PRIMARY KEY,
    -- Thẻ luôn thuộc một bộ. Xóa bộ thì DB tự xóa thẻ.
    deck_id        BIGINT       NOT NULL REFERENCES decks (id) ON DELETE CASCADE,
    word           VARCHAR(100) NOT NULL,
    part_of_speech VARCHAR(20)  NOT NULL,
    phonetic       VARCHAR(100),
    definition     VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Chống trùng: trong một bộ không có 2 thẻ trùng cả từ lẫn nghĩa, không phân biệt hoa thường.
-- "Apple / quả táo" và "apple / Quả táo" là trùng; "light / ánh sáng" và "light / nhẹ" thì không.
-- Index này cũng tăng tốc truy vấn "lấy thẻ của bộ" (WHERE deck_id = ?) vì deck_id đứng đầu,
-- nên không cần thêm index riêng cho deck_id.
CREATE UNIQUE INDEX uq_cards_deck_word_definition
    ON cards (deck_id, lower(word), lower(definition));

-- Câu ví dụ của thẻ (@ElementCollection trong Card.java). Mỗi câu một dòng.
CREATE TABLE card_examples (
    -- Xóa thẻ thì DB tự xóa ví dụ.
    card_id  BIGINT       NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    -- Thứ tự câu ví dụ (@OrderColumn), bắt đầu từ 0.
    position INTEGER      NOT NULL,
    example  VARCHAR(500) NOT NULL,
    PRIMARY KEY (card_id, position)
);

-- ============================================================================
-- V10: Tách "thẻ" thành Từ (words) → Nghĩa (senses), bộ từ thành nhãn chủ đề (deck_words).
--
--   TRƯỚC                                SAU
--   decks ─< cards (1 từ + 1 nghĩa)      words (sổ từ: của user, hoặc của hệ thống khi owner_id NULL)
--             └─< card_examples            ├─< senses (1 nghĩa)  ←  CHÍNH LÀ bảng cards cũ, đổi tên
--   card_progresses → cards                │     └─< sense_examples
--                                          └─<> deck_words >─ decks   (1 từ nằm trong nhiều bộ)
--                                        card_progresses → senses (tiến độ theo TỪNG NGHĨA)
--
-- Đổi tên cards → senses (không tạo bảng mới) để GIỮ NGUYÊN id: tiến độ ôn, bài làm cũ không mất.
-- ============================================================================

-- 1. Bảng từ. owner_id NULL = từ của hệ thống (thuộc bộ hệ thống, admin quản lý).
CREATE TABLE words (
    id         BIGSERIAL    PRIMARY KEY,
    owner_id   BIGINT       REFERENCES users (id) ON DELETE CASCADE,
    term       VARCHAR(100) NOT NULL,
    phonetic   VARCHAR(100),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Mỗi người chỉ có MỘT dòng cho mỗi từ (không phân biệt hoa thường): "Light" và "light" là một.
-- Hai index riêng vì UNIQUE coi các giá trị NULL là khác nhau → (NULL, 'light') trùng vẫn lọt.
CREATE UNIQUE INDEX uq_words_owner_term  ON words (owner_id, lower(term)) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_words_system_term ON words (lower(term))           WHERE owner_id IS NULL;

-- 2. Bộ ↔ từ (nhiều-nhiều).
CREATE TABLE deck_words (
    deck_id  BIGINT      NOT NULL REFERENCES decks (id) ON DELETE CASCADE,
    word_id  BIGINT      NOT NULL REFERENCES words (id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (deck_id, word_id)
);
-- PK (deck_id, word_id) không giúp tra "từ này nằm trong những bộ nào" (word_id đứng sau).
CREATE INDEX idx_deck_words_word_id ON deck_words (word_id);

-- 3. Tạo words từ cards cũ: gom theo (chủ của bộ, lower(word)).
--    Bộ hệ thống → owner NULL. Phiên âm lấy của thẻ cũ nhất có phiên âm.
INSERT INTO words (owner_id, term, phonetic, created_at)
SELECT d.owner_id,
       (array_agg(c.word ORDER BY c.id))[1],                            -- giữ cách viết của thẻ tạo đầu tiên
       (array_agg(c.phonetic ORDER BY c.id) FILTER (WHERE c.phonetic IS NOT NULL))[1],
       min(c.created_at)
FROM cards c
JOIN decks d ON d.id = c.deck_id
GROUP BY d.owner_id, lower(c.word);

-- 4. Đổi cards → senses, gắn word_id.
ALTER TABLE cards RENAME TO senses;
ALTER TABLE senses ADD COLUMN word_id  BIGINT REFERENCES words (id) ON DELETE CASCADE;
ALTER TABLE senses ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

UPDATE senses s
SET word_id = w.id
FROM decks d, words w
WHERE d.id = s.deck_id
  AND lower(w.term) = lower(s.word)
  AND w.owner_id IS NOT DISTINCT FROM d.owner_id;   -- "IS NOT DISTINCT FROM": so sánh được cả NULL = NULL

-- 5. deck_words từ quan hệ cũ (thẻ thuộc bộ nào → từ thuộc bộ đó).
INSERT INTO deck_words (deck_id, word_id, added_at)
SELECT deck_id, word_id, min(created_at) FROM senses GROUP BY deck_id, word_id;

-- 6. Gộp nghĩa trùng: cùng một user có "light / nhẹ" ở 2 bộ khác nhau → giờ là 2 nghĩa trùng của 1 từ.
--    Giữ nghĩa có id NHỎ nhất. Tiến độ của bản bị xóa: chuyển sang bản giữ lại nếu bản đó chưa có.
WITH dup AS (
  SELECT id, min(id) OVER (PARTITION BY word_id, lower(definition)) AS keep_id
  FROM senses
)
UPDATE card_progresses p
SET card_id = dup.keep_id
FROM dup
WHERE p.card_id = dup.id AND dup.id <> dup.keep_id
  AND NOT EXISTS (SELECT 1 FROM card_progresses q WHERE q.user_id = p.user_id AND q.card_id = dup.keep_id);

-- Câu ví dụ của bản bị xóa: nối vào cuối danh sách ví dụ của bản giữ lại (không mất câu nào).
WITH dup AS (
  SELECT id, min(id) OVER (PARTITION BY word_id, lower(definition)) AS keep_id
  FROM senses
)
INSERT INTO card_examples (card_id, position, example)
SELECT dup.keep_id,
       (SELECT coalesce(max(position), -1) FROM card_examples k WHERE k.card_id = dup.keep_id)
         + row_number() OVER (PARTITION BY dup.keep_id ORDER BY e.card_id, e.position),
       e.example
FROM dup
JOIN card_examples e ON e.card_id = dup.id
WHERE dup.id <> dup.keep_id;

DELETE FROM senses s
USING senses k
WHERE k.word_id = s.word_id AND lower(k.definition) = lower(s.definition) AND k.id < s.id;

-- 7. Thứ tự nghĩa trong từ: theo thứ tự thẻ được tạo.
UPDATE senses s
SET position = r.rn - 1
FROM (SELECT id, row_number() OVER (PARTITION BY word_id ORDER BY created_at, id) AS rn FROM senses) r
WHERE r.id = s.id;

-- 8. Dọn cột cũ (đã chuyển lên words / deck_words), khóa lại ràng buộc.
DROP INDEX uq_cards_deck_word_definition;
ALTER TABLE senses DROP COLUMN deck_id;
ALTER TABLE senses DROP COLUMN word;
ALTER TABLE senses DROP COLUMN phonetic;
ALTER TABLE senses ALTER COLUMN word_id SET NOT NULL;
ALTER TABLE senses ALTER COLUMN position DROP DEFAULT;
ALTER INDEX cards_pkey RENAME TO senses_pkey;
ALTER SEQUENCE cards_id_seq RENAME TO senses_id_seq;
-- Một từ không có 2 nghĩa trùng nhau. Index này cũng phục vụ "lấy các nghĩa của từ" (word_id đứng đầu).
CREATE UNIQUE INDEX uq_senses_word_definition ON senses (word_id, lower(definition));

-- 9. Câu ví dụ và tiến độ: chỉ đổi tên, dữ liệu giữ nguyên.
ALTER TABLE card_examples RENAME TO sense_examples;
ALTER TABLE sense_examples RENAME COLUMN card_id TO sense_id;
ALTER TABLE card_progresses RENAME COLUMN card_id TO sense_id;
ALTER INDEX idx_card_progresses_card_id RENAME TO idx_card_progresses_sense_id;

-- 10. Nhật ký luyện tập: mỗi lần trả lời ở MỌI chế độ (flashcard, điền từ, xếp câu) = 1 dòng.
--     Nuôi trang thống kê và "Sổ lỗi sai". Leitner vẫn nằm ở card_progresses.
CREATE TABLE review_events (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sense_id    BIGINT      NOT NULL REFERENCES senses (id) ON DELETE CASCADE,
    mode        VARCHAR(20) NOT NULL CHECK (mode IN ('FLASHCARD', 'FILL_BLANK', 'SENTENCE_BUILD')),
    correct     BOOLEAN     NOT NULL,
    duration_ms INTEGER     NOT NULL CHECK (duration_ms >= 0),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- "Lịch sử luyện của tôi, mới nhất trước" và "các lần sai gần đây".
CREATE INDEX idx_review_events_user_time ON review_events (user_id, reviewed_at DESC);
CREATE INDEX idx_review_events_sense_id  ON review_events (sense_id);

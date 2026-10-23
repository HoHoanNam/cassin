-- Nhóm ngữ pháp: chủ đề → câu hỏi → 4 đáp án.

CREATE TABLE grammar_topics (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    level       VARCHAR(20)  NOT NULL,
    -- TEXT: không giới hạn độ dài, dùng cho bài giải thích dài.
    explanation TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE questions (
    id          BIGSERIAL PRIMARY KEY,
    -- Xóa chủ đề thì DB tự xóa câu hỏi.
    topic_id    BIGINT      NOT NULL REFERENCES grammar_topics (id) ON DELETE CASCADE,
    content     TEXT        NOT NULL,
    explanation TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Truy vấn "lấy ngẫu nhiên 10 câu của chủ đề X" (WHERE topic_id = ?) cần index.
CREATE INDEX idx_questions_topic_id ON questions (topic_id);

CREATE TABLE question_options (
    id          BIGSERIAL PRIMARY KEY,
    -- Xóa câu hỏi thì DB tự xóa đáp án.
    question_id BIGINT       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    content     VARCHAR(255) NOT NULL,
    is_correct  BOOLEAN      NOT NULL,
    -- Vị trí hiển thị: 0..3 (Question.OPTION_COUNT = 4).
    position    INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 3),

    -- Mỗi vị trí trong một câu hỏi chỉ có một đáp án.
    -- Kết hợp với CHECK ở trên: một câu hỏi có TỐI ĐA 4 đáp án.
    -- Ràng buộc này cũng tạo index (question_id, position), nên không cần index riêng cho question_id.
    CONSTRAINT uq_question_options_question_position UNIQUE (question_id, position)
);

-- Rule "đúng 1 đáp án đúng" chỉ kiểm tra trong Java (Question.validateOptions), KHÔNG kiểm tra ở DB.
-- Lý do: khi admin đổi đáp án đúng từ A sang B, Hibernate chạy 2 câu UPDATE lần lượt.
-- Nếu B được cập nhật trước, giữa 2 câu sẽ có lúc tồn tại 2 đáp án đúng → ràng buộc ở DB
-- sẽ báo lỗi dù kết quả cuối cùng hoàn toàn hợp lệ.

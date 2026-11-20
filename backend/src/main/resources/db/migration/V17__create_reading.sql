-- Nhóm đọc hiểu: bài đọc → câu hỏi (3 dạng) → gợi ý từ đồng nghĩa; lượt làm bài → câu trả lời.
--
-- 3 dạng câu hỏi nằm CHUNG một bảng reading_questions (JPA: @Inheritance SINGLE_TABLE),
-- phân biệt bằng cột question_type. Cột riêng của dạng nào thì dạng khác để NULL;
-- CHECK chk_reading_questions_type_columns bảo đảm không có dòng "nửa nọ nửa kia".
--
-- Quyết định giữ lịch sử (giống V7): bài đã có người làm thì KHÔNG xóa được (khóa ngoại NO ACTION);
-- admin chuyển bài sang ARCHIVED để ẩn khỏi danh sách.

CREATE TABLE passages (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(150) NOT NULL,
    -- Nội dung bài đọc; xuống dòng 2 lần = sang đoạn mới (FE tách đoạn để đánh số Paragraph A, B...).
    content      TEXT         NOT NULL,
    level        VARCHAR(2)   NOT NULL CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    -- Tính trong Java mỗi lần đổi nội dung, để hiện "~3 phút đọc" mà không đếm lại.
    word_count   INTEGER      NOT NULL CHECK (word_count >= 0),
    -- Admin bị xóa thì bài vẫn còn.
    created_by   BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,

    -- Bài nháp chưa từng publish; bài đã/đang publish phải có thời điểm publish.
    CONSTRAINT chk_passages_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status <> 'DRAFT' AND published_at IS NOT NULL)
    )
);

-- Danh sách cho học viên: WHERE status = 'PUBLISHED' AND level = ? ORDER BY published_at DESC.
CREATE INDEX idx_passages_status_level_published ON passages (status, level, published_at DESC);

CREATE TABLE reading_questions (
    id            BIGSERIAL PRIMARY KEY,
    passage_id    BIGINT      NOT NULL REFERENCES passages (id) ON DELETE CASCADE,
    position      INTEGER     NOT NULL CHECK (position BETWEEN 0 AND 19),
    question_type VARCHAR(10) NOT NULL CHECK (question_type IN ('MCQ', 'TFNG', 'BLANK')),
    -- MCQ: câu hỏi; TFNG: câu nhận định; BLANK: câu có đúng một chỗ trống "____".
    prompt        TEXT        NOT NULL,
    -- Câu trích NGUYÊN VĂN trong bài làm bằng chứng (Java kiểm tra nó nằm trong content).
    -- FE tô sáng câu này sau khi nộp. NOT_GIVEN thì ghi câu gần nhất liên quan.
    evidence      TEXT        NOT NULL,
    explanation   TEXT        NOT NULL,

    -- Chỉ dạng TFNG.
    tfng_answer   VARCHAR(10) CHECK (tfng_answer IN ('TRUE', 'FALSE', 'NOT_GIVEN')),
    -- Chỉ dạng BLANK: loại đáp án + số từ tối đa.
    --   WORDS               : chỉ chữ, tối đa max_words từ           ("ONE WORD ONLY")
    --   NUMBER              : đúng một số                            ("A NUMBER")
    --   WORDS_AND_OR_NUMBER : tối đa max_words từ và/hoặc một số     ("NO MORE THAN TWO WORDS AND/OR A NUMBER")
    answer_kind   VARCHAR(20) CHECK (answer_kind IN ('WORDS', 'NUMBER', 'WORDS_AND_OR_NUMBER')),
    max_words     INTEGER     CHECK (max_words BETWEEN 1 AND 3),

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_reading_questions_passage_position UNIQUE (passage_id, position),
    -- Cột của dạng nào chỉ được dùng cho dạng đó.
    CONSTRAINT chk_reading_questions_type_columns CHECK (
        (question_type = 'MCQ'   AND tfng_answer IS NULL     AND answer_kind IS NULL     AND max_words IS NULL) OR
        (question_type = 'TFNG'  AND tfng_answer IS NOT NULL AND answer_kind IS NULL     AND max_words IS NULL) OR
        (question_type = 'BLANK' AND tfng_answer IS NULL     AND answer_kind IS NOT NULL AND
            -- NUMBER không cần max_words; hai loại còn lại bắt buộc có.
            ((answer_kind = 'NUMBER' AND max_words IS NULL) OR (answer_kind <> 'NUMBER' AND max_words IS NOT NULL)))
    )
);

-- Chỉ dạng MCQ: 4 lựa chọn A..D. "Đúng 1 đáp án đúng" kiểm tra trong Java (lý do như V6).
CREATE TABLE reading_options (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT       NOT NULL REFERENCES reading_questions (id) ON DELETE CASCADE,
    content     VARCHAR(255) NOT NULL,
    is_correct  BOOLEAN      NOT NULL,
    position    INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 3),
    CONSTRAINT uq_reading_options_question_position UNIQUE (question_id, position)
);

-- Chỉ dạng BLANK: các đáp án được chấp nhận ("solar power", "solar energy").
-- JPA: @ElementCollection + @OrderColumn(name = "position") trên BlankQuestion.
CREATE TABLE reading_blank_answers (
    question_id BIGINT       NOT NULL REFERENCES reading_questions (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 4),
    answer      VARCHAR(100) NOT NULL,
    PRIMARY KEY (question_id, position)
);

-- Gợi ý theo cặp diễn đạt lại (paraphrase): câu hỏi dùng "replacement", bài dùng "alternative".
-- Mở lần lượt theo position, tối đa 3 gợi ý mỗi câu. Không gửi xuống FE trước khi học viên xin.
CREATE TABLE reading_hints (
    id              BIGSERIAL PRIMARY KEY,
    question_id     BIGINT       NOT NULL REFERENCES reading_questions (id) ON DELETE CASCADE,
    position        INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 2),
    -- Cụm từ trong câu hỏi.
    question_phrase VARCHAR(100) NOT NULL,
    -- Cụm đồng nghĩa trong bài (Java kiểm tra nó có trong content, không phân biệt hoa thường).
    passage_phrase  VARCHAR(100) NOT NULL,
    CONSTRAINT uq_reading_hints_question_position UNIQUE (question_id, position)
);

CREATE TABLE reading_attempts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Không ON DELETE: chặn xóa bài đã có người làm.
    passage_id   BIGINT      NOT NULL REFERENCES passages (id),
    score        INTEGER     CHECK (score >= 0),
    total        INTEGER     NOT NULL CHECK (total BETWEEN 1 AND 20),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    CONSTRAINT chk_reading_attempts_submitted_score CHECK (
        (submitted_at IS NULL AND score IS NULL) OR
        (submitted_at IS NOT NULL AND score IS NOT NULL AND score <= total)
    )
);

CREATE INDEX idx_reading_attempts_user_started ON reading_attempts (user_id, started_at DESC);
CREATE INDEX idx_reading_attempts_passage_id   ON reading_attempts (passage_id);

-- Tạo sẵn một dòng cho MỖI câu khi bắt đầu lượt làm, để ghi hints_used ngay cả khi chưa trả lời.
CREATE TABLE reading_answers (
    id                 BIGSERIAL PRIMARY KEY,
    attempt_id         BIGINT       NOT NULL REFERENCES reading_attempts (id) ON DELETE CASCADE,
    question_id        BIGINT       NOT NULL REFERENCES reading_questions (id),
    position           INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 19),
    -- Mỗi dạng dùng một cột; cả ba NULL = bỏ trống.
    selected_option_id BIGINT       REFERENCES reading_options (id),
    tfng_answer        VARCHAR(10)  CHECK (tfng_answer IN ('TRUE', 'FALSE', 'NOT_GIVEN')),
    text_answer        VARCHAR(100),
    correct            BOOLEAN      NOT NULL DEFAULT false,
    -- BLANK sai định dạng: 'OVER_WORD_LIMIT', 'NUMBER_REQUIRED'... để FE giải thích vì sao sai.
    rejected_reason    VARCHAR(30),
    hints_used         INTEGER      NOT NULL DEFAULT 0 CHECK (hints_used BETWEEN 0 AND 3),
    CONSTRAINT uq_reading_answers_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT uq_reading_answers_attempt_position UNIQUE (attempt_id, position)
);

CREATE INDEX idx_reading_answers_question_id        ON reading_answers (question_id);
CREATE INDEX idx_reading_answers_selected_option_id ON reading_answers (selected_option_id);
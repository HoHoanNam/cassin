-- Nhóm quiz: một lượt làm bài (quiz_attempts) gồm nhiều câu trả lời (attempt_answers).
--
-- Quyết định: CHẶN xóa câu hỏi / đáp án / chủ đề đã có người làm, để giữ nguyên lịch sử học tập.
-- Cách làm: khóa ngoại KHÔNG ghi ON DELETE → mặc định là NO ACTION (giống RESTRICT):
-- DB từ chối xóa dòng đang được tham chiếu.

CREATE TABLE quiz_attempts (
    id           BIGSERIAL PRIMARY KEY,
    -- Xóa người dùng thì xóa luôn lịch sử làm bài của họ (dữ liệu của chính họ).
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Không ON DELETE: chặn xóa chủ đề đã có người làm.
    topic_id     BIGINT      NOT NULL REFERENCES grammar_topics (id),
    -- Null khi chưa nộp.
    score        INTEGER     CHECK (score >= 0),
    total        INTEGER     NOT NULL CHECK (total BETWEEN 1 AND 10),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,

    -- Chưa nộp thì chưa có điểm; nộp rồi thì phải có điểm, và điểm không vượt quá số câu.
    CONSTRAINT chk_quiz_attempts_submitted_score CHECK (
        (submitted_at IS NULL AND score IS NULL) OR
        (submitted_at IS NOT NULL AND score IS NOT NULL AND score <= total)
    )
);

-- Lịch sử làm bài của tôi, mới nhất trước (WHERE user_id = ? ORDER BY started_at DESC).
CREATE INDEX idx_quiz_attempts_user_started ON quiz_attempts (user_id, started_at DESC);
-- Khi admin xóa chủ đề, DB phải tìm xem có lượt làm bài nào trỏ tới nó không → cần index.
CREATE INDEX idx_quiz_attempts_topic_id ON quiz_attempts (topic_id);

CREATE TABLE attempt_answers (
    id                 BIGSERIAL PRIMARY KEY,
    -- Xóa lượt làm bài thì xóa luôn câu trả lời của nó.
    quiz_attempt_id    BIGINT  NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    -- Không ON DELETE: chặn xóa câu hỏi / đáp án đã có người làm.
    question_id        BIGINT  NOT NULL REFERENCES questions (id),
    -- Null = bỏ trống.
    selected_option_id BIGINT  REFERENCES question_options (id),
    correct            BOOLEAN NOT NULL DEFAULT false,
    position           INTEGER NOT NULL CHECK (position BETWEEN 0 AND 9),

    -- Một câu hỏi chỉ xuất hiện một lần trong một lượt.
    CONSTRAINT uq_attempt_answers_attempt_question UNIQUE (quiz_attempt_id, question_id),
    -- Mỗi vị trí trong đề chỉ có một câu. Index này cũng phục vụ truy vấn "các câu của lượt X".
    CONSTRAINT uq_attempt_answers_attempt_position UNIQUE (quiz_attempt_id, position)
);

-- Hai index phục vụ việc "chặn xóa": khi admin xóa câu hỏi / đáp án,
-- DB phải tìm xem có câu trả lời nào trỏ tới không. Không có index → quét cả bảng.
CREATE INDEX idx_attempt_answers_question_id        ON attempt_answers (question_id);
CREATE INDEX idx_attempt_answers_selected_option_id ON attempt_answers (selected_option_id);

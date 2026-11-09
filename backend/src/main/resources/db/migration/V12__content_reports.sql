CREATE TABLE content_reports (
    id           BIGSERIAL    PRIMARY KEY,
    reporter_id  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sense_id     BIGINT       NOT NULL REFERENCES senses (id) ON DELETE CASCADE,
    reason       VARCHAR(30)  NOT NULL CHECK (reason IN ('WRONG_DEFINITION', 'WRONG_EXAMPLE',
                                                     'WRONG_PART_OF_SPEECH', 'TYPO', 'OTHER')),
    message      VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'REJECTED')),
    admin_note   VARCHAR(500),
    resolved_by  BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Đang mở ⇔ chưa có thời điểm xử lý. Code quên gán resolved_at thì DB từ chối.
    CONSTRAINT chk_reports_closed CHECK ((status = 'OPEN') = (resolved_at IS NULL))
);

-- Mỗi người chỉ có MỘT báo cáo ĐANG MỞ cho một nghĩa. Đã đóng thì báo lại được.
CREATE UNIQUE INDEX uq_reports_open_per_user_sense ON content_reports (reporter_id, sense_id) WHERE status = 'OPEN';
-- Admin lọc theo trạng thái, mới nhất trước.
CREATE INDEX idx_reports_status_created   ON content_reports (status, created_at DESC);
-- Học viên xem báo cáo của mình.
CREATE INDEX idx_reports_reporter_created ON content_reports (reporter_id, created_at DESC);
CREATE INDEX idx_reports_sense_id         ON content_reports (sense_id);
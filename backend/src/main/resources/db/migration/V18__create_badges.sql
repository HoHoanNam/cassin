-- Huy hiệu (gamification). Thiết kế đầy đủ: docs/badges/README.md
--
--   badge_definitions  1 ──< badge_tiers  1 ──< user_badges >── 1 users
--   (15 huy hiệu)          (35 cấp: mốc)       (cấp mỗi người đã đạt)
--
-- Hai loại huy hiệu:
--   TIERED  : 3 cấp I (Đồng) → II (Bạc) → III (Vàng), mốc tăng dần. Vd Chăm chỉ: 30 / 300 / 1500 lượt.
--   SPECIAL : đúng 1 cấp, cho khoảnh khắc đặc biệt; secret = TRUE thì chỉ lộ tên khi đã đạt.
--
-- Danh mục là DỮ LIỆU CỐ ĐỊNH, seed ngay ở đây, khớp 1-1 với frontend/src/app/core/mock/mock-badge-catalog.ts.
-- Luật tính tiến độ nằm trong code (mỗi huy hiệu một bean BadgeRule); huy hiệu chưa có rule thì API chưa trả ra,
-- nên làm từng danh mục một không cần thêm migration.
--
-- Không có tiến độ lưu sẵn: tiến độ = một câu COUNT trên bảng nghiệp vụ, tính lúc cần.
-- Chỉ việc ĐÃ ĐẠT được ghi (user_badges), và ghi rồi thì giữ mãi (chuỗi ngày bị đứt cũng không mất cấp).

CREATE TABLE badge_definitions (
    -- Mã ổn định, không đổi sau khi phát hành (FE map mã → hình vẽ).
    code        VARCHAR(40)  PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('TIERED', 'SPECIAL')),
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(200) NOT NULL,
    -- Đơn vị đếm hiện cạnh số: "lượt", "ngày liên tiếp", "nghĩa"...
    unit        VARCHAR(30)  NOT NULL,
    category    VARCHAR(20)  NOT NULL CHECK (category IN ('REVIEW', 'STREAK', 'MASTERY', 'COLLECTION', 'QUIZ',
                                                          'READING', 'PRACTICE', 'COMMUNITY', 'SPECIAL')),
    -- Bí mật: chưa đạt thì API che tên / mô tả / tiến độ.
    secret      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Thứ tự hiển thị trên trang Thành tích.
    sort_order  INTEGER      NOT NULL UNIQUE,

    -- Huy hiệu bí mật chỉ có thể là loại đặc biệt (huy hiệu có cấp luôn hiện mốc kế tiếp).
    CONSTRAINT chk_badge_definitions_secret_special CHECK (NOT secret OR kind = 'SPECIAL')
);

CREATE TABLE badge_tiers (
    badge_code VARCHAR(40) NOT NULL REFERENCES badge_definitions (code) ON DELETE CASCADE,
    level      SMALLINT    NOT NULL CHECK (level BETWEEN 1 AND 3),
    -- Mốc cần đạt (>= target). Mốc tăng dần theo cấp được kiểm ở cuối file sau khi seed.
    target     INTEGER     NOT NULL CHECK (target > 0),
    PRIMARY KEY (badge_code, level)
);

CREATE TABLE user_badges (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_code VARCHAR(40) NOT NULL,
    level      SMALLINT    NOT NULL,
    earned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL: chưa xem popup "Mở khóa huy hiệu". GET /api/badges/unseen đọc đúng các dòng này.
    seen_at    TIMESTAMPTZ,

    -- Mỗi (người, huy hiệu, cấp) chỉ một dòng. BadgeAwardService ghi bằng
    -- INSERT ... ON CONFLICT DO NOTHING → hai request song song cũng không cấp trùng.
    PRIMARY KEY (user_id, badge_code, level),
    -- Chỉ cấp được cấp có thật (vd không có "Đa năng cấp 2").
    FOREIGN KEY (badge_code, level) REFERENCES badge_tiers (badge_code, level),
    CONSTRAINT chk_user_badges_seen_after_earned CHECK (seen_at IS NULL OR seen_at >= earned_at)
);

-- Popup: WHERE user_id = ? AND seen_at IS NULL. Index một phần: chỉ chứa các dòng chưa xem (rất ít).
CREATE INDEX idx_user_badges_unseen ON user_badges (user_id) WHERE seen_at IS NULL;
-- Khóa ngoại tới badge_tiers: Postgres không tự tạo index cho phía tham chiếu.
CREATE INDEX idx_user_badges_badge_level ON user_badges (badge_code, level);

-- ============================================================================
-- Seed danh mục
-- ============================================================================

INSERT INTO badge_definitions (code, kind, name, description, unit, category, secret, sort_order) VALUES
    -- Có cấp: I (Đồng) · II (Bạc) · III (Vàng)
    ('DILIGENT',      'TIERED',  'Chăm chỉ',       'Trả lời thẻ khi ôn tập và luyện tập.',             'lượt',           'REVIEW',     FALSE, 10),
    ('ON_FIRE',       'TIERED',  'Lửa bền',        'Học liên tục nhiều ngày, không ngắt quãng.',       'ngày liên tiếp', 'STREAK',     FALSE, 20),
    ('MEMORY_KEEPER', 'TIERED',  'Nhớ lâu',        'Đưa nghĩa của từ lên hộp 5 (đã thuộc).',           'nghĩa',          'MASTERY',    FALSE, 30),
    ('COLLECTOR',     'TIERED',  'Nhà sưu tầm',    'Tự thêm từ mới vào kho của bạn.',                  'từ',             'COLLECTION', FALSE, 40),
    ('PERFECTIONIST', 'TIERED',  'Điểm tuyệt đối', 'Làm đúng hết bài kiểm tra ngữ pháp.',              'bài',            'QUIZ',       FALSE, 50),
    ('GRAMMARIAN',    'TIERED',  'Nhà ngữ pháp',   'Nộp bài kiểm tra ở nhiều chủ đề khác nhau.',       'chủ đề',         'QUIZ',       FALSE, 60),
    ('BOOKWORM',      'TIERED',  'Mọt sách',       'Hoàn thành bài đọc hiểu.',                         'bài',            'READING',    FALSE, 70),
    ('SELF_RELIANT',  'TIERED',  'Tự lực',         'Đúng hết một bài đọc mà không mở gợi ý nào.',      'bài',            'READING',    FALSE, 80),
    ('WORDSMITH',     'TIERED',  'Thợ rèn câu',    'Trả lời bài điền từ và xếp câu.',                  'câu',            'PRACTICE',   FALSE, 90),
    ('SHARP_EYE',     'TIERED',  'Mắt tinh',       'Báo lỗi nội dung và được quản trị viên sửa.',      'báo lỗi',        'COMMUNITY',  FALSE, 100),
    -- Đặc biệt: một lần
    ('ALL_ROUNDER',   'SPECIAL', 'Đa năng',        'Thử đủ 3 kiểu luyện: lật thẻ, điền từ, xếp câu.',  'kiểu',           'SPECIAL',    FALSE, 110),
    ('NEVER_GIVE_UP', 'SPECIAL', 'Không bỏ cuộc',  'Thuộc được một nghĩa từng quên ít nhất 3 lần.',    'nghĩa',          'SPECIAL',    FALSE, 120),
    ('COMEBACK',      'SPECIAL', 'Trở lại',        'Quay lại học sau 7 ngày trở lên tạm nghỉ.',        'lần',            'SPECIAL',    FALSE, 130),
    ('NIGHT_OWL',     'SPECIAL', 'Cú đêm',         'Ôn thẻ sau 23 giờ.',                               'lần',            'SPECIAL',    TRUE,  140),
    ('EARLY_BIRD',    'SPECIAL', 'Chim sớm',       'Ôn thẻ trước 6 giờ sáng.',                         'lần',            'SPECIAL',    TRUE,  150);

INSERT INTO badge_tiers (badge_code, level, target) VALUES
    ('DILIGENT',      1, 30), ('DILIGENT',      2, 300), ('DILIGENT',      3, 1500),
    ('ON_FIRE',       1, 3),  ('ON_FIRE',       2, 14),  ('ON_FIRE',       3, 60),
    ('MEMORY_KEEPER', 1, 5),  ('MEMORY_KEEPER', 2, 50),  ('MEMORY_KEEPER', 3, 300),
    ('COLLECTOR',     1, 20), ('COLLECTOR',     2, 100), ('COLLECTOR',     3, 300),
    ('PERFECTIONIST', 1, 1),  ('PERFECTIONIST', 2, 5),   ('PERFECTIONIST', 3, 20),
    ('GRAMMARIAN',    1, 3),  ('GRAMMARIAN',    2, 6),   ('GRAMMARIAN',    3, 10),
    ('BOOKWORM',      1, 3),  ('BOOKWORM',      2, 10),  ('BOOKWORM',      3, 30),
    ('SELF_RELIANT',  1, 1),  ('SELF_RELIANT',  2, 5),   ('SELF_RELIANT',  3, 15),
    ('WORDSMITH',     1, 30), ('WORDSMITH',     2, 300), ('WORDSMITH',     3, 1000),
    ('SHARP_EYE',     1, 1),  ('SHARP_EYE',     2, 5),   ('SHARP_EYE',     3, 20),
    ('ALL_ROUNDER',   1, 3),
    ('NEVER_GIVE_UP', 1, 1),
    ('COMEBACK',      1, 1),
    ('NIGHT_OWL',     1, 1),
    ('EARLY_BIRD',    1, 1);

-- ============================================================================
-- Tự kiểm tra seed: sai thì migration dừng luôn, không để dữ liệu lệch lọt vào DB.
-- ============================================================================
DO $$
DECLARE
    bad TEXT;
BEGIN
    -- TIERED đủ đúng 3 cấp 1, 2, 3; SPECIAL đúng 1 cấp là cấp 1.
    SELECT string_agg(code, ', ') INTO bad
    FROM (
        SELECT d.code, d.kind, array_agg(t.level ORDER BY t.level) AS levels
        FROM badge_definitions d
        LEFT JOIN badge_tiers t ON t.badge_code = d.code
        GROUP BY d.code, d.kind
    ) x
    WHERE (kind = 'TIERED'  AND levels IS DISTINCT FROM ARRAY[1, 2, 3]::SMALLINT[])
       OR (kind = 'SPECIAL' AND levels IS DISTINCT FROM ARRAY[1]::SMALLINT[]);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'badge_tiers sai số cấp: %', bad;
    END IF;

    -- Mốc tăng ngặt theo cấp.
    SELECT string_agg(DISTINCT a.badge_code, ', ') INTO bad
    FROM badge_tiers a
    JOIN badge_tiers b ON b.badge_code = a.badge_code AND b.level = a.level + 1
    WHERE b.target <= a.target;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'badge_tiers mốc không tăng dần: %', bad;
    END IF;
END $$;

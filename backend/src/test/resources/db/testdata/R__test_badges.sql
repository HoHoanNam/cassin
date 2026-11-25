-- Huy hiệu GIẢ chỉ dành cho test. Chỉ nạp ở profile it (application-it.yml thêm classpath:db/testdata vào
-- spring.flyway.locations), không bao giờ có trong DB thật.
--
-- Vì sao cần: BadgeBaseIT kiểm phần chung (nhảy cấp, che bí mật, đánh dấu đã xem...) bằng rule giả. Nếu rule giả
-- mượn mã thật (DILIGENT...), context của test có HAI rule cùng mã → BadgeRuleRegistry dừng app. Mã TEST_* không
-- bao giờ có rule thật, nên các phase thêm rule bao nhiêu cũng không đụng tới.
--
-- R__ (repeatable): không có số phiên bản, chạy SAU mọi V__ và chạy lại khi nội dung file đổi.
-- Đặt V1000__ thì sau này thêm V19 thật sẽ "nhỏ hơn bản đã chạy" → Flyway báo lỗi trên DB giữ lại giữa các lần chạy.
-- ON CONFLICT DO NOTHING: chạy lại bao nhiêu lần cũng an toàn.
INSERT INTO badge_definitions (code, kind, name, description, unit, category, secret, sort_order) VALUES
    ('TEST_TIERED',   'TIERED',  'Test có cấp',   'Huy hiệu giả có 3 cấp.',   'lượt', 'REVIEW',  FALSE, 9001),
    ('TEST_SECRET',   'SPECIAL', 'Test bí mật',   'Huy hiệu giả bí mật.',     'lần',  'SPECIAL', TRUE,  9002),
    ('TEST_DISABLED', 'SPECIAL', 'Test chưa bật', 'Có trong DB, không rule.', 'lần',  'SPECIAL', FALSE, 9003)
ON CONFLICT (code) DO NOTHING;

INSERT INTO badge_tiers (badge_code, level, target) VALUES
    ('TEST_TIERED',   1, 30), ('TEST_TIERED', 2, 300), ('TEST_TIERED', 3, 1500),
    ('TEST_SECRET',   1, 1),
    ('TEST_DISABLED', 1, 1)
ON CONFLICT DO NOTHING;

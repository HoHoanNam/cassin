-- [THÊM] Thông tin thiết bị cho "Quản lý phiên đăng nhập" (GET /api/auth/sessions).
--
-- Mỗi HỌ token (family_id) = một lần đăng nhập = một thiết bị. Xoay vòng giữ đúng MỘT token sống mỗi họ,
-- nên token đang sống chính là dòng "thiết bị" để hiển thị. Hai cột mới được CHÉP sang token mới mỗi lần refresh:
--
--   user_agent    header User-Agent lúc đăng nhập → "Chrome · Windows" (DeviceLabel)
--   signed_in_at  thời điểm đăng nhập của cả họ
--
-- "Hoạt động gần nhất" KHÔNG cần cột riêng: mỗi lần refresh cấp token mới với created_at = lúc đó.
ALTER TABLE refresh_tokens
    ADD COLUMN user_agent   VARCHAR(255),
    ADD COLUMN signed_in_at TIMESTAMPTZ;

-- Dòng có sẵn: lấy token sớm nhất CÒN LẠI của họ làm mốc đăng nhập (dòng cũ hơn có thể đã bị job dọn dẹp xóa).
UPDATE refresh_tokens t
SET signed_in_at = f.first_at
FROM (SELECT family_id, min(created_at) AS first_at FROM refresh_tokens GROUP BY family_id) f
WHERE t.family_id = f.family_id;

ALTER TABLE refresh_tokens ALTER COLUMN signed_in_at SET NOT NULL;

-- Liệt kê phiên: token sống của một user. idx_refresh_tokens_user_active (V14, partial WHERE revoked_at IS NULL)
-- đã phủ đúng điều kiện này, không cần index mới.

-- ============================================================================
-- V13: optimistic locking cho 2 bảng có thể bị sửa CÙNG LÚC.
--
--   card_progresses : double click "Nhớ" / mở 2 tab cùng ôn một nghĩa
--   content_reports : hai admin cùng xử lý một báo cáo
--
-- Entity có @Version → mỗi UPDATE Hibernate tự viết:
--   UPDATE ... SET ..., version = version + 1 WHERE id = ? AND version = <số đã đọc>
-- Có người sửa trước → 0 dòng khớp → OptimisticLockException → 409 CONCURRENT_UPDATE.
--
-- DEFAULT 0: các dòng đã có đều bắt đầu từ version 0, không cần UPDATE dữ liệu cũ.
-- Lần INSERT đầu (chưa có dòng nào để so version) do unique index chặn trùng:
--   uq_card_progresses_user_card (V5), uq_reports_open_per_user_sense (V12).
-- ============================================================================
ALTER TABLE card_progresses ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE content_reports ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

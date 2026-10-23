-- Không sửa V1, V2: Flyway đã chạy chúng và lưu checksum, sửa file cũ thì app không khởi động.
-- Mọi thay đổi schema sau khi đã chạy → viết file migration mới.

-- 1. Enum DeckType đã đổi ADMIN_DECK → SYSTEM_DECK, nhưng CHECK trong V2 vẫn dùng 'ADMIN_DECK'.
--    Nếu không sửa, mọi lệnh tạo bộ hệ thống đều bị DB từ chối.
--    Phải xóa CHECK trước, rồi mới đổi dữ liệu cũ (nếu có), rồi tạo lại CHECK.
ALTER TABLE decks DROP CONSTRAINT chk_decks_type_owner;

UPDATE decks SET deck_type = 'SYSTEM_DECK' WHERE deck_type = 'ADMIN_DECK';

ALTER TABLE decks ADD CONSTRAINT chk_decks_type_owner CHECK (
    (deck_type = 'USER_DECK'   AND owner_id IS NOT NULL) OR
    (deck_type = 'SYSTEM_DECK' AND owner_id IS NULL)
);

-- 2. Thêm updated_at cho users và decks. DEFAULT now() điền sẵn giá trị cho các dòng đã có.
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE decks ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

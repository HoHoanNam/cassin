import { BadgeCode } from '../../../core/models/badge.models';

/**
 * [THÊM] Hình giữa huy chương: path SVG trên lưới 24×24, vẽ bằng NÉT DÀY màu trắng (kiểu sticker),
 * có bóng màu tối của nhóm phía sau. Mỗi hình chỉ 2-4 nét lớn để vẫn đọc được ở cỡ 48px.
 * Backend chỉ lưu MÃ huy hiệu; hình là việc của frontend (đổi hình không cần migration).
 */
export const BADGE_GLYPHS: Record<BadgeCode, string> = {
  // Ôn thẻ: chồng thẻ
  DILIGENT: 'M4 9.5h11v10.5H4zM8 5.5h11.5V16',
  // Chuỗi ngày: ngọn lửa có lõi
  ON_FIRE:
    'M12 2.5c1.2 3.6 5.5 5.6 5.5 10.6a5.5 5.5 0 0 1-11 0c0-2.7 1.6-4.3 2.7-5.4.3 1.6 1.1 2.7 2.1 3.2 0-3.2.5-5.8.7-8.4zM12 14.5c1 1 1.8 1.8 1.8 3a1.8 1.8 0 0 1-3.6 0c0-1.2.8-2 1.8-3z',
  // Thuộc từ: hộp số 5 (khối hộp Leitner)
  MEMORY_KEEPER: 'M4 8l8-4 8 4v8.5l-8 4-8-4zM4 8l8 4 8-4M12 12v8.5',
  // Kho từ: sách mở có dấu trang
  COLLECTOR:
    'M3 5.5c3-1 6-1 9 1 3-2 6-2 9-1V19c-3-1-6-1-9 1-3-2-6-2-9-1zM12 6.5V20M16 4v5l1.5-1 1.5 1V4.3',
  // Ngữ pháp: dấu tích trong vòng tròn
  PERFECTIONIST: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18M7.8 12.2l3 3 5.4-6.4',
  // Ngữ pháp: mũ tốt nghiệp
  GRAMMARIAN: 'M2 9.5l10-5 10 5-10 5zM6 11.5V16c3.2 2.2 8.8 2.2 12 0v-4.5M21.5 9.5v5.5',
  // Đọc hiểu: kính đọc sách
  BOOKWORM:
    'M3 14.5a3.5 3.5 0 1 0 7 0 3.5 3.5 0 0 0-7 0M14 14.5a3.5 3.5 0 1 0 7 0 3.5 3.5 0 0 0-7 0M10 14.5h4M3.2 13.5L5 7.5M20.8 13.5L19 7.5',
  // Đọc hiểu: cắm cờ trên đỉnh núi (tự mình leo lên)
  SELF_RELIANT: 'M2.5 20.5l7-11 3.5 5 2-2.8 6.5 8.8zM9.5 9.5V3.5l5 1.8-5 1.9',
  // Luyện tập: bút viết + dòng chữ
  WORDSMITH: 'M3.5 20.5l1-4 10.5-10.5 3 3L7.5 19.5zM13 8l3 3M13.5 20.5h7',
  // Cộng đồng: kính lúp có dấu tích
  SHARP_EYE: 'M10 3.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13M15 15l5.5 5.5M7.3 10.2l2 2 3.2-3.7',
  // Đặc biệt
  ALL_ROUNDER:
    'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18M12 7.5a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9M12 11.3a.7.7 0 1 1 0 1.4.7.7 0 0 1 0-1.4',
  NEVER_GIVE_UP:
    'M12 21v-8.5M12 12.5C12 8.5 9 6 4.5 6c0 4.3 3 6.5 7.5 6.5zM12 10.5c0-4 3-6.5 7.5-6.5 0 4.3-3 6.5-7.5 6.5z',
  COMEBACK: 'M8.5 7H4V2.5M4.3 7A8.5 8.5 0 1 1 3.6 14M12 8v4.5l3 2',
  NIGHT_OWL:
    'M19.5 14.5A8 8 0 1 1 9.5 4.5a6.3 6.3 0 0 0 10 10zM16 4l.6 1.6 1.6.6-1.6.6L16 8.4l-.6-1.6-1.6-.6 1.6-.6z',
  EARLY_BIRD:
    'M2.5 18.5h19M6.5 18.5a5.5 5.5 0 0 1 11 0M12 4.5v3.5M4.6 9.6l2.2 2.2M19.4 9.6l-2.2 2.2',
};

/** Huy hiệu bí mật chưa đạt: dấu hỏi. */
export const SECRET_GLYPH = 'M8.8 8.8a3.2 3.2 0 1 1 4.4 3c-.8.3-1.2 1-1.2 1.8v1M12 18.6v.2';

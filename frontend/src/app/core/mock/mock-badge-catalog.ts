import { BadgeCategory, BadgeCode, BadgeKind } from '../models/badge.models';
import { MAX_BOX } from '../models/review.models';
import { addDays, todayIso } from '../util/dates';
import { MockDb } from './mock-db';

/**
 * [THÊM] Danh mục huy hiệu + luật tính tiến độ.
 *
 * Ở backend: định nghĩa nằm trong badge_definitions + badge_tiers (Flyway V18 seed),
 * luật là các bean BadgeRule (mỗi luật một câu COUNT trên bảng sẵn có).
 * Mock gom cả hai vào đây: progress(db, userId) đọc thẳng các "bảng" của MockDb.
 *
 * Nguyên tắc chọn mốc:
 *   - Cấp I đạt được trong 1-3 ngày đầu (người mới thấy ngay phần thưởng, nhưng không "cho không"
 *     kiểu "đăng nhập lần đầu").
 *   - Cấp II cần vài tuần học đều. Cấp III là mục tiêu dài hạn, chỉ người chăm nhất mới có.
 */
export interface BadgeDefinition {
  code: BadgeCode;
  kind: BadgeKind;
  name: string;
  description: string;
  unit: string;
  category: BadgeCategory;
  secret: boolean;
  /** Mốc từng cấp, tăng dần. SPECIAL: [1] */
  targets: number[];
  progress: (db: MockDb, userId: number) => number;
}

const reviewsOf = (db: MockDb, userId: number) => db.reviewEvents.filter((e) => e.userId === userId);
const submittedQuizzes = (db: MockDb, userId: number) =>
  db.attempts.filter((a) => a.userId === userId && a.submittedAt !== null);
const submittedReadings = (db: MockDb, userId: number) =>
  db.readingAttempts.filter((a) => a.userId === userId && a.submittedAt !== null);

/** Số lần trả lời rơi vào khung giờ [from, to) theo giờ máy người học. */
const reviewsInHours = (db: MockDb, userId: number, from: number, to: number) =>
  reviewsOf(db, userId).filter((e) => {
    const hour = new Date(e.reviewedAt).getHours();
    return hour >= from && hour < to;
  }).length;

/**
 * Chuỗi ngày học hiện tại: đếm lùi từ hôm nay (hoặc hôm qua nếu hôm nay chưa học)
 * miễn ngày nào cũng có trong study_days. Cùng cách tính với streakDays ở /api/review/stats.
 */
export function currentStreak(days: string[]): number {
  const set = new Set(days);
  let cursor = todayIso();
  if (!set.has(cursor)) {
    cursor = addDays(cursor, -1);
  }
  let count = 0;
  while (set.has(cursor)) {
    count += 1;
    cursor = addDays(cursor, -1);
  }
  return count;
}

/** Có lần nào quay lại học sau ít nhất `gap` ngày nghỉ liền không? */
function hasComeback(days: string[], gap: number): boolean {
  const sorted = [...days].sort();
  return sorted.some((day, i) => i > 0 && day > addDays(sorted[i - 1], gap));
}

export const BADGE_CATALOG: BadgeDefinition[] = [
  // ================= Có cấp: I (Đồng) · II (Bạc) · III (Vàng) =================
  {
    code: 'DILIGENT', kind: 'TIERED', name: 'Chăm chỉ', description: 'Trả lời thẻ khi ôn tập và luyện tập.',
    unit: 'lượt', category: 'REVIEW', secret: false, targets: [30, 300, 1500],
    progress: (db, u) => reviewsOf(db, u).length,
  },
  {
    code: 'ON_FIRE', kind: 'TIERED', name: 'Lửa bền', description: 'Học liên tục nhiều ngày, không ngắt quãng.',
    unit: 'ngày liên tiếp', category: 'STREAK', secret: false, targets: [3, 14, 60],
    progress: (db, u) => currentStreak(db.studyDays(u)),
  },
  {
    code: 'MEMORY_KEEPER', kind: 'TIERED', name: 'Nhớ lâu', description: 'Đưa nghĩa của từ lên hộp 5 (đã thuộc).',
    unit: 'nghĩa', category: 'MASTERY', secret: false, targets: [5, 50, 300],
    progress: (db, u) => db.progresses.filter((p) => p.userId === u && p.box >= MAX_BOX).length,
  },
  {
    code: 'COLLECTOR', kind: 'TIERED', name: 'Nhà sưu tầm', description: 'Tự thêm từ mới vào kho của bạn.',
    unit: 'từ', category: 'COLLECTION', secret: false, targets: [20, 100, 300],
    progress: (db, u) => db.words.filter((w) => w.ownerId === u).length,
  },
  {
    code: 'PERFECTIONIST', kind: 'TIERED', name: 'Điểm tuyệt đối', description: 'Làm đúng hết bài kiểm tra ngữ pháp.',
    unit: 'bài', category: 'QUIZ', secret: false, targets: [1, 5, 20],
    progress: (db, u) => submittedQuizzes(db, u).filter((a) => a.score === a.total).length,
  },
  {
    code: 'GRAMMARIAN', kind: 'TIERED', name: 'Nhà ngữ pháp', description: 'Nộp bài kiểm tra ở nhiều chủ đề khác nhau.',
    unit: 'chủ đề', category: 'QUIZ', secret: false, targets: [3, 6, 10],
    progress: (db, u) => new Set(submittedQuizzes(db, u).map((a) => a.topicId)).size,
  },
  {
    code: 'BOOKWORM', kind: 'TIERED', name: 'Mọt sách', description: 'Hoàn thành bài đọc hiểu.',
    unit: 'bài', category: 'READING', secret: false, targets: [3, 10, 30],
    progress: (db, u) => submittedReadings(db, u).length,
  },
  {
    code: 'SELF_RELIANT', kind: 'TIERED', name: 'Tự lực', description: 'Đúng hết một bài đọc mà không mở gợi ý nào.',
    unit: 'bài', category: 'READING', secret: false, targets: [1, 5, 15],
    progress: (db, u) =>
      submittedReadings(db, u).filter((a) => a.score === a.total && a.answers.every((x) => x.hintsUsed === 0)).length,
  },
  {
    code: 'WORDSMITH', kind: 'TIERED', name: 'Thợ rèn câu', description: 'Trả lời bài điền từ và xếp câu.',
    unit: 'câu', category: 'PRACTICE', secret: false, targets: [30, 300, 1000],
    progress: (db, u) => reviewsOf(db, u).filter((e) => e.mode !== 'FLASHCARD').length,
  },
  {
    code: 'SHARP_EYE', kind: 'TIERED', name: 'Mắt tinh', description: 'Báo lỗi nội dung và được quản trị viên sửa.',
    unit: 'báo lỗi', category: 'COMMUNITY', secret: false, targets: [1, 5, 20],
    progress: (db, u) => db.reports.filter((r) => r.reporterId === u && r.status === 'RESOLVED').length,
  },

  // ================= Đặc biệt: một lần, cho khoảnh khắc đáng nhớ =================
  {
    code: 'ALL_ROUNDER', kind: 'SPECIAL', name: 'Đa năng', description: 'Thử đủ 3 kiểu luyện: lật thẻ, điền từ, xếp câu.',
    unit: 'kiểu', category: 'SPECIAL', secret: false, targets: [3],
    progress: (db, u) => new Set(reviewsOf(db, u).map((e) => e.mode)).size,
  },
  {
    code: 'NEVER_GIVE_UP', kind: 'SPECIAL', name: 'Không bỏ cuộc',
    description: 'Thuộc được một nghĩa từng quên ít nhất 3 lần.',
    unit: 'nghĩa', category: 'SPECIAL', secret: false, targets: [1],
    progress: (db, u) => db.progresses.filter((p) => p.userId === u && p.box >= MAX_BOX && p.lapseCount >= 3).length,
  },
  {
    code: 'COMEBACK', kind: 'SPECIAL', name: 'Trở lại', description: 'Quay lại học sau 7 ngày trở lên tạm nghỉ.',
    unit: 'lần', category: 'SPECIAL', secret: false, targets: [1],
    progress: (db, u) => (hasComeback(db.studyDays(u), 7) ? 1 : 0),
  },
  {
    code: 'NIGHT_OWL', kind: 'SPECIAL', name: 'Cú đêm', description: 'Ôn thẻ sau 23 giờ.',
    unit: 'lần', category: 'SPECIAL', secret: true, targets: [1],
    progress: (db, u) => reviewsInHours(db, u, 23, 24),
  },
  {
    code: 'EARLY_BIRD', kind: 'SPECIAL', name: 'Chim sớm', description: 'Ôn thẻ trước 6 giờ sáng.',
    unit: 'lần', category: 'SPECIAL', secret: true, targets: [1],
    progress: (db, u) => reviewsInHours(db, u, 0, 6),
  },
];

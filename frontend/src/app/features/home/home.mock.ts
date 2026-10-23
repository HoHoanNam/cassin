import { HomeData } from './home.models';

/**
 * Dữ liệu giả cho trang chủ khi chưa có API.
 * Xóa file này khi HomeService gọi backend thật.
 */
export const HOME_MOCK: HomeData = {
  displayName: 'Nam',
  review: {
    dueToday: 18,
    boxCounts: [24, 31, 40, 52, 51],
    streakDays: 5,
  },
  decks: [
    { id: 1, name: '1000 từ thông dụng', type: 'SYSTEM_DECK', cardCount: 120, dueCount: 12 },
    { id: 2, name: 'Từ vựng IELTS Writing', type: 'USER_DECK', cardCount: 48, dueCount: 6 },
    { id: 3, name: 'Phrasal verbs hay gặp', type: 'USER_DECK', cardCount: 30, dueCount: 0 },
  ],
  grammarTopics: [
    { id: 1, title: 'Thì hiện tại hoàn thành', level: 'A2', questionCount: 24 },
    { id: 2, title: 'Câu bị động', level: 'B1', questionCount: 18 },
    { id: 3, title: 'Câu điều kiện loại 1 và 2', level: 'B1', questionCount: 20 },
    { id: 4, title: 'Mệnh đề quan hệ', level: 'B2', questionCount: 15 },
  ],
  recentQuizzes: [
    { id: 31, topicTitle: 'Câu bị động', score: 8, total: 10, submittedAt: '2026-10-22T13:20:00Z' },
    { id: 30, topicTitle: 'Thì hiện tại hoàn thành', score: 6, total: 10, submittedAt: '2026-10-21T02:05:00Z' },
    { id: 28, topicTitle: 'Câu điều kiện loại 1 và 2', score: 10, total: 10, submittedAt: '2026-10-18T11:45:00Z' },
  ],
};

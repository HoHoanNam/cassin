import { Routes } from '@angular/router';

/**
 * Mỗi trang được lazy load: code của trang chỉ tải khi người dùng mở trang đó.
 * Tham số :id được truyền vào input "id" của component (xem withComponentInputBinding trong app.config.ts).
 */
export const routes: Routes = [
  {
    path: '',
    title: 'Cassin',
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
  },
  {
    // Ôn tất cả thẻ đến hạn, không phân biệt bộ.
    path: 'review',
    title: 'Ôn thẻ | Cassin',
    loadComponent: () => import('./features/review/review-page').then((m) => m.ReviewPage),
  },
  {
    path: 'decks/:id',
    title: 'Bộ từ | Cassin',
    loadComponent: () => import('./features/decks/deck-detail-page').then((m) => m.DeckDetailPage),
  },
  {
    // Ôn riêng một bộ: cùng trang với /review, có thêm id.
    path: 'decks/:id/review',
    title: 'Ôn thẻ | Cassin',
    loadComponent: () => import('./features/review/review-page').then((m) => m.ReviewPage),
  },
  {
    path: 'grammar/:id/quiz',
    title: 'Bài kiểm tra | Cassin',
    loadComponent: () => import('./features/quiz/quiz-page').then((m) => m.QuizPage),
  },
  // Trang chưa có: quay về trang chủ.
  { path: '**', redirectTo: '' },
];

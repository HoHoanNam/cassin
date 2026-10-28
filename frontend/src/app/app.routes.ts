import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';
import { quizLeaveGuard } from './features/quiz/quiz-leave.guard';

/**
 * Sơ đồ trang. Mọi trang đều lazy load: code của trang chỉ tải khi người dùng mở trang đó.
 *
 *   Khách (chưa đăng nhập)        : /  /login  /register
 *   Khu vực học (có sidebar)      : /today /decks /decks/:deckId /grammar /grammar/:topicId /quiz-history /quiz/:attemptId/result
 *   Chế độ tập trung (không menu) : /review  /decks/:deckId/review  /grammar/:topicId/quiz
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Cassin: ghi từ vựng như ghi vở, nhớ lâu như thuộc bài',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/landing/landing-page').then((m) => m.LandingPage),
  },
  {
    path: 'login',
    title: 'Đăng nhập | Cassin',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Tạo tài khoản | Cassin',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register-page').then((m) => m.RegisterPage),
  },

  // ---------- Chế độ tập trung: toàn màn hình, chỉ có nút Thoát ----------
  {
    path: 'review',
    title: 'Ôn thẻ | Cassin',
    canActivate: [authGuard],
    loadComponent: () => import('./features/review/review-page').then((m) => m.ReviewPage),
  },
  {
    path: 'decks/:deckId/review',
    title: 'Ôn bộ từ | Cassin',
    canActivate: [authGuard],
    loadComponent: () => import('./features/review/review-page').then((m) => m.ReviewPage),
  },
  {
    path: 'grammar/:topicId/quiz',
    title: 'Làm bài kiểm tra | Cassin',
    canActivate: [authGuard],
    // Hỏi lại trước khi rời trang khi đang làm bài dở.
    canDeactivate: [quizLeaveGuard],
    loadComponent: () => import('./features/quiz/quiz-page').then((m) => m.QuizPage),
  },

  // ---------- Khu vực học: có sidebar / thanh dưới đáy ----------
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/app-shell/app-shell').then((m) => m.AppShell),
    children: [
      {
        path: 'today',
        title: 'Hôm nay | Cassin',
        loadComponent: () => import('./features/today/today-page').then((m) => m.TodayPage),
      },
      {
        path: 'decks',
        title: 'Bộ từ | Cassin',
        loadComponent: () => import('./features/decks/decks-page').then((m) => m.DecksPage),
      },
      {
        path: 'decks/:deckId',
        title: 'Bộ từ | Cassin',
        loadComponent: () => import('./features/decks/deck-detail-page').then((m) => m.DeckDetailPage),
      },
      {
        path: 'grammar',
        title: 'Ngữ pháp | Cassin',
        loadComponent: () => import('./features/grammar/grammar-page').then((m) => m.GrammarPage),
      },
      {
        path: 'grammar/:topicId',
        title: 'Chủ đề ngữ pháp | Cassin',
        loadComponent: () => import('./features/grammar/topic-page').then((m) => m.TopicPage),
      },
      {
        path: 'quiz-history',
        title: 'Lịch sử làm bài | Cassin',
        loadComponent: () => import('./features/quiz/quiz-history-page').then((m) => m.QuizHistoryPage),
      },
      {
        path: 'quiz/:attemptId/result',
        title: 'Kết quả bài kiểm tra | Cassin',
        loadComponent: () => import('./features/quiz/quiz-result-page').then((m) => m.QuizResultPage),
      },
      {
        path: '**',
        title: 'Không tìm thấy trang | Cassin',
        loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
      },
    ],
  },
];

import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/auth.guards';
import { quizLeaveGuard } from './features/quiz/quiz-leave.guard';

/**
 * Sơ đồ trang. Mọi trang đều lazy load: code của trang chỉ tải khi người dùng mở trang đó.
 *
 *   Khách (chưa đăng nhập)        : /  /login  /register
 *   Khu vực học (có sidebar)      : /today /decks /decks/:deckId /library /scan /practice /mistakes /grammar /grammar/:topicId
 *                                   /quiz-history /quiz/:attemptId/result /account /reports
 *   Quản trị (chỉ ADMIN)          : /admin  /admin/decks(/:deckId)  /admin/grammar(/:topicId)  /admin/reports  /admin/passages
 *   Chế độ tập trung (không menu) : /review  /decks/:deckId/review  /grammar/:topicId/quiz  /practice/session
 *                                   /reading/:passageId                                        [THÊM] làm bài đọc
 *   [THÊM] Đọc hiểu (có sidebar)  : /reading  /reading/attempts/:attemptId/result
 *   [THÊM] Quản trị bài đọc       : /admin/passages  /admin/passages/new  /admin/passages/:passageId
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
    // [THÊM] ?mode=fill-blank|sentence-build&deckId=&count=
    path: 'practice/session',
    title: 'Luyện tập | Cassin',
    canActivate: [authGuard],
    loadComponent: () => import('./features/practice/practice-session-page').then((m) => m.PracticeSessionPage),
  },
  {
    path: 'grammar/:topicId/quiz',
    title: 'Làm bài kiểm tra | Cassin',
    canActivate: [authGuard],
    // Hỏi lại trước khi rời trang khi đang làm bài dở.
    canDeactivate: [quizLeaveGuard],
    loadComponent: () => import('./features/quiz/quiz-page').then((m) => m.QuizPage),
  },

  {
    // [THÊM] Làm bài đọc: mở trang = bắt đầu (hoặc làm tiếp) một lượt. Nháp câu trả lời lưu trong trình duyệt nên không cần guard.
    path: 'reading/:passageId',
    title: 'Làm bài đọc | Cassin',
    canActivate: [authGuard],
    loadComponent: () => import('./features/reading/reading-attempt-page').then((m) => m.ReadingAttemptPage),
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
        path: 'library',
        title: 'Thư viện từ | Cassin',
        loadComponent: () => import('./features/library/library-page').then((m) => m.LibraryPage),
      },
      {
        path: 'scan',
        title: 'Quét đoạn văn | Cassin',
        loadComponent: () => import('./features/scan/scan-page').then((m) => m.ScanPage),
      },
      // [THÊM]
      {
        path: 'practice',
        title: 'Luyện tập | Cassin',
        loadComponent: () => import('./features/practice/practice-page').then((m) => m.PracticePage),
      },
      {
        path: 'mistakes',
        title: 'Sổ lỗi sai | Cassin',
        loadComponent: () => import('./features/practice/mistakes-page').then((m) => m.MistakesPage),
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
      // [THÊM] Đọc hiểu
      {
        path: 'reading',
        title: 'Đọc hiểu | Cassin',
        loadComponent: () => import('./features/reading/reading-page').then((m) => m.ReadingPage),
      },
      {
        path: 'reading/attempts/:attemptId/result',
        title: 'Kết quả bài đọc | Cassin',
        loadComponent: () => import('./features/reading/reading-result-page').then((m) => m.ReadingResultPage),
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
        path: 'account',
        title: 'Tài khoản | Cassin',
        loadComponent: () => import('./features/account/account-page').then((m) => m.AccountPage),
      },
      // [THÊM]
      {
        path: 'reports',
        title: 'Báo lỗi của tôi | Cassin',
        loadComponent: () => import('./features/reports/my-reports-page').then((m) => m.MyReportsPage),
      },

      // ---------- Khu vực quản trị: chỉ ADMIN (backend vẫn chặn thật bằng 403) ----------
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/admin-layout/admin-layout').then((m) => m.AdminLayout),
        children: [
          {
            path: '',
            title: 'Quản trị | Cassin',
            loadComponent: () => import('./features/admin/admin-overview-page').then((m) => m.AdminOverviewPage),
          },
          {
            path: 'decks',
            title: 'Bộ từ hệ thống | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-decks-page').then((m) => m.AdminDecksPage),
          },
          {
            path: 'decks/:deckId',
            title: 'Bộ từ hệ thống | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-deck-page').then((m) => m.AdminDeckPage),
          },
          {
            path: 'grammar',
            title: 'Ngữ pháp | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-topics-page').then((m) => m.AdminTopicsPage),
          },
          {
            path: 'grammar/:topicId',
            title: 'Chủ đề ngữ pháp | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-topic-page').then((m) => m.AdminTopicPage),
          },
          // [THÊM] Bài đọc hiểu
          {
            path: 'passages',
            title: 'Bài đọc | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-passages-page').then((m) => m.AdminPassagesPage),
          },
          {
            path: 'passages/new',
            title: 'Soạn bài đọc | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-passage-page').then((m) => m.AdminPassagePage),
          },
          {
            path: 'passages/:passageId',
            title: 'Soạn bài đọc | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-passage-page').then((m) => m.AdminPassagePage),
          },
          // [THÊM]
          {
            path: 'reports',
            title: 'Báo lỗi nội dung | Quản trị Cassin',
            loadComponent: () => import('./features/admin/admin-reports-page').then((m) => m.AdminReportsPage),
          },
        ],
      },
      {
        path: '**',
        title: 'Không tìm thấy trang | Cassin',
        loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
      },
    ],
  },
];

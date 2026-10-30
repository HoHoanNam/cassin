import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withViewTransitions,
} from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor, errorInterceptor } from './core/auth/http.interceptors';
import { provideApi } from './core/api/provide-api';
import { APP_ENV } from './core/config/app-env';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      // Tham số trên URL (:deckId...) được truyền thẳng vào input() cùng tên của component trang.
      withComponentInputBinding(),
      // Chuyển trang thì cuộn lên đầu; bấm link #id thì cuộn tới đúng mục.
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
      // Hiệu ứng chuyển trang mượt (trình duyệt không hỗ trợ thì bỏ qua, không lỗi).
      withViewTransitions(),
    ),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // Mock hay API thật: chọn bằng lệnh chạy (npm start / npm run start:mock), xem src/environments/
    provideApi(APP_ENV.useMockApi),
  ],
};

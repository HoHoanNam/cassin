import { environment } from '../../../environments/environment';

/**
 * Cấu hình chạy của frontend.
 *
 * useMockApi lấy từ src/environments/:
 *   npm start           → environment.ts       → false: gọi backend thật tại apiBaseUrl (qua proxy.conf.json)
 *   npm run start:mock  → environment.mock.ts  → true : dữ liệu giả trong trình duyệt, không cần backend
 */
export const APP_ENV = {
  useMockApi: environment.useMockApi,
  apiBaseUrl: '/api',
  /** Độ trễ giả lập của mock (ms), để thấy được trạng thái đang tải như khi gọi mạng thật. */
  mockLatencyMs: { min: 250, max: 650 },
} as const;

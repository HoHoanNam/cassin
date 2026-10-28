/**
 * Cấu hình chạy của frontend.
 *
 * useMockApi = true  : dùng dữ liệu giả lưu trong trình duyệt (localStorage), không cần backend.
 *                      Tài khoản có sẵn: nam@gmail.com / 12345678
 * useMockApi = false : gọi backend thật tại apiBaseUrl (chạy kèm proxy, xem proxy.conf.json).
 */
export const APP_ENV = {
  useMockApi: true,
  apiBaseUrl: '/api',
  /** Độ trễ giả lập của mock (ms), để thấy được trạng thái đang tải như khi gọi mạng thật. */
  mockLatencyMs: { min: 250, max: 650 },
} as const;

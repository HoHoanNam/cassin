import { Observable, map, timer } from 'rxjs';

import { APP_ENV } from '../config/app-env';
import { ApiError } from '../models/common.models';

/**
 * Giả lập một lần gọi mạng: chờ một khoảng ngẫu nhiên rồi mới chạy handler.
 * Handler ném ApiError → Observable báo lỗi, giống hệt API thật trả 4xx.
 * Nhờ độ trễ này, trạng thái "đang tải" (skeleton) và nút "Đang lưu..." đều thấy được khi làm giao diện.
 */
export function simulate<T>(handler: () => T): Observable<T> {
  const { min, max } = APP_ENV.mockLatencyMs;
  const ms = min + Math.round(Math.random() * (max - min));
  return timer(ms).pipe(map(() => handler()));
}

export const notFound = (code: string, message: string) => new ApiError(404, code, message);
export const conflict = (code: string, message: string) => new ApiError(409, code, message);
export const forbidden = (code: string, message: string) => new ApiError(403, code, message);
export const badRequest = (message: string, fieldErrors: Record<string, string> = {}) =>
  new ApiError(400, 'VALIDATION_ERROR', message, fieldErrors);

/** Giống luật @Valid ở backend: gom lỗi từng field, có lỗi thì ném 400 kèm fieldErrors. */
export function validate(rules: [field: string, failed: boolean, message: string][]): void {
  const fieldErrors: Record<string, string> = {};
  for (const [field, failed, message] of rules) {
    if (failed && !fieldErrors[field]) {
      fieldErrors[field] = message;
    }
  }
  if (Object.keys(fieldErrors).length > 0) {
    throw badRequest('Dữ liệu chưa hợp lệ', fieldErrors);
  }
}

export const isBlank = (value: string | null | undefined) => !value || value.trim().length === 0;
export const tooLong = (value: string | null | undefined, max: number) => (value ?? '').trim().length > max;

/** Xáo trộn mảng (Fisher-Yates), dùng khi lấy câu hỏi ngẫu nhiên. */
export function shuffle<T>(items: T[]): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

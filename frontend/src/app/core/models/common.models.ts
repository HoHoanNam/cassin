/**
 * Kiểu dữ liệu dùng chung, khớp 1-1 với backend (package com.cassin.common.dto).
 * Giữ đúng tên field của Java record để chuyển từ mock sang API thật không phải sửa gì.
 */

/** Khớp PageResponse<T> của backend. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Khớp ErrorResponse của backend: mọi lỗi API đều có dạng này. */
export interface ErrorResponse {
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
}

/**
 * Lỗi API đã chuẩn hóa, dùng trong toàn bộ frontend.
 * Cả mock lẫn HTTP thật đều ném ra ApiError, nên component chỉ cần xử lý MỘT loại lỗi.
 *   status = 0 nghĩa là không kết nối được server (mất mạng, server tắt).
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }

  static network(): ApiError {
    return new ApiError(0, 'NETWORK_ERROR', 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.');
  }
}

/** Lấy câu thông báo dễ hiểu từ một lỗi bất kỳ. */
export function errorMessage(error: unknown, fallback = 'Đã có lỗi xảy ra. Vui lòng thử lại.'): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return fallback;
}

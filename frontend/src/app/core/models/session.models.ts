/**
 * [THÊM] Thiết bị đang đăng nhập. Khớp com.cassin.auth.dto.SessionResponse ở backend.
 * Một thiết bị = một "họ" refresh token; id là familyId, không đổi qua các lần làm mới token.
 */
export interface Session {
  id: string;
  /** "Chrome", "Safari"... hoặc "Không rõ" */
  browser: string;
  /** "Windows", "iOS"... hoặc "Không rõ" */
  os: string;
  /** ISO-8601: lúc đăng nhập trên thiết bị này */
  signedInAt: string;
  /** ISO-8601: lần làm mới token gần nhất (sai lệch tối đa 15 phút) */
  lastActiveAt: string;
  /** Thiết bị đang xem trang này */
  current: boolean;
}

/** Khớp RevokedSessionsResponse */
export interface RevokedSessions {
  revoked: number;
}

/** Hệ điều hành di động → icon điện thoại, còn lại → màn hình */
export const MOBILE_OS = new Set(['iOS', 'iPadOS', 'Android']);

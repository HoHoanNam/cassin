import { Observable } from 'rxjs';

import { LoginRequest, LoginResponse, RegisterRequest, User } from '../models/user.models';

/**
 * "Hợp đồng" của nhóm API đăng nhập.
 *
 * Mọi API đều viết theo cùng một kiểu: abstract class (chỉ khai báo method) + 2 bản cài đặt:
 *   - Mock...Api : dữ liệu giả trong trình duyệt, chạy được khi chưa có backend
 *   - Http...Api : gọi backend thật bằng HttpClient
 * Component chỉ inject(AuthApi), không biết đang dùng bản nào.
 * Đổi giữa mock và thật: chọn lệnh chạy, `npm start` (thật) hoặc `npm run start:mock` (mock).
 *
 * Dùng abstract class (không dùng interface) vì Angular cần một thứ CÒN TỒN TẠI lúc chạy
 * để làm khóa inject; interface của TypeScript biến mất sau khi biên dịch.
 */
export abstract class AuthApi {
  /** POST /api/auth/register → 201 */
  abstract register(request: RegisterRequest): Observable<User>;
  /** POST /api/auth/login → 200 */
  abstract login(request: LoginRequest): Observable<LoginResponse>;
  /** GET /api/me */
  abstract me(): Observable<User>;
}

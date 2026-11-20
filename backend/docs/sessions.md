# Quản lý phiên đăng nhập — "Thiết bị đang đăng nhập"

Trang Tài khoản liệt kê các thiết bị đang đăng nhập, cho đăng xuất từng thiết bị hoặc mọi thiết bị khác.
Xây trên refresh token có sẵn (xem [refresh-token.md](refresh-token.md)), chỉ thêm 2 cột.

```
┌──────────────────────────────────────────────────────────────────┐
│ Thiết bị đang đăng nhập                                           │
│ ● Chrome · Windows   Đăng nhập 18/11 · Đang dùng   (thiết bị này)  │
│ ● Safari · iOS       Đăng nhập 10/11 · 2 giờ trước     [Đăng xuất] │
│                                    [Đăng xuất mọi thiết bị khác]   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 1. Một thiết bị = một họ token

Mỗi lần đăng nhập mở một họ (`family_id`). Xoay vòng giữ **đúng một token sống** mỗi họ,
nên "danh sách thiết bị" chính là "các token đang sống của user". Không cần bảng mới.

```
refresh_tokens (user Nam)
 họ A ── t1 ✗ ── t2 ✗ ── t3 ●   ← Chrome · Windows   (thiết bị này)
 họ B ── t4 ✗ ── t5 ●           ← Safari · iOS
 họ D ── t7 ✗                   ← đã đăng xuất → không hiện
```

| Thông tin hiển thị | Lấy từ |
|---|---|
| `id` | `family_id`: không đổi qua các lần refresh. Biết id không làm được gì khác, muốn refresh vẫn cần token gốc |
| Trình duyệt · Hệ điều hành | `user_agent` (V16) → `DeviceLabel` |
| Đăng nhập lúc | `signed_in_at` (V16), chép sang token mới mỗi lần xoay |
| Hoạt động gần nhất | `created_at` của token đang sống = lần refresh gần nhất. Sai lệch tối đa 15 phút (TTL access token) |
| Thiết bị này | Họ của cookie refresh đi kèm request |

## 2. Luồng của `user_agent`

```
POST /api/auth/login   (header User-Agent) → startSession(user, userAgent)  → họ mới, signed_in_at = now
POST /api/auth/refresh                      → rotate(): token mới CHÉP user_agent + signed_in_at từ token cũ
PUT  /api/me/password  (header User-Agent) → revokeAll + startSession(user, userAgent)
```

`DeviceLabel` nhận diện bằng chuỗi con, **thứ tự là điểm dễ sai**: UA của Edge và Opera đều chứa `Chrome/`,
UA của Chrome chứa `Safari/`, UA của iPad chứa `Mac OS X`, UA của Android chứa `Linux`.
Nên xét Edge, Opera trước Chrome; Chrome trước Safari; iPhone, iPad trước macOS; Android trước Linux.

## 3. API

| Endpoint | Kết quả |
|---|---|
| `GET /api/auth/sessions` | `[{ id, browser, os, signedInAt, lastActiveAt, current }]`, mới hoạt động nhất lên đầu |
| `DELETE /api/auth/sessions/{id}` | 204. Họ không có / đã thu hồi / của người khác → **404 SESSION_NOT_FOUND** (cùng một lỗi). Thu hồi chính thiết bị đang dùng → kèm xóa cookie |
| `DELETE /api/auth/sessions` | `{ revoked: n }`: thu hồi mọi họ **trừ** họ hiện tại. Không có cookie → thu hồi tất cả |

**Vì sao `/api/auth/sessions` chứ không phải `/api/me/sessions`:** cookie refresh có `Path=/api/auth`,
trình duyệt chỉ gửi cookie tới đường dẫn đó. Không có cookie thì không biết đâu là "thiết bị này".

Mỗi request cần **cả hai**: Bearer access token (biết user là ai) + cookie (biết thiết bị nào).

## 4. Hai cái bẫy

**SecurityConfig.** `/api/auth/**` đang `permitAll`. Rule `/api/auth/sessions/**` → `authenticated()`
phải đặt **trước** dòng đó (luật khớp đầu tiên thắng). Thiếu thì API mở công khai, `user` null → 500.

**FE interceptor.** Trước đây mọi URL `/api/auth/*` bị coi là "request đăng nhập": không gắn Bearer,
gặp 401 không tự refresh. Nay chỉ `login`, `register`, `refresh`, `logout` mới được bỏ qua.

## 5. Giới hạn

| Giới hạn | Hướng mở rộng |
|---|---|
| Thu hồi phiên chặn refresh ngay, nhưng access token đã cấp còn dùng được tối đa 15 phút | Đưa `familyId` vào JWT, filter kiểm tra họ còn sống (thêm 1 truy vấn mỗi request) |
| Không giới hạn số thiết bị | Đăng nhập thứ N+1 tự thu hồi họ hoạt động lâu nhất |
| Không lưu IP / vị trí | Cố ý: dữ liệu cá nhân, không cần cho một trang học từ vựng |

## 6. Test

| File | Nội dung |
|---|---|
| `DeviceLabelTest` | 13 UA thật → nhãn, gồm các ca "mượn tên" (Edge/Opera/Samsung chứa Chrome, iPad chứa Mac OS X) |
| `RefreshTokenTest`, `RefreshTokenServiceTest` | Cắt UA 255 ký tự; xoay vòng chép thiết bị + lúc đăng nhập; current; 404; giữ phiên hiện tại |
| `SessionIT` | 8 kịch bản qua HTTP + PostgreSQL: danh sách, refresh giữ id, đăng xuất 1 / chính mình / người khác, đăng xuất mọi thiết bị khác, thiếu Bearer → 401, đăng xuất + đổi mật khẩu |

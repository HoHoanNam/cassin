# Cassin – Frontend (Angular 22)

## Chạy

```bash
npm install
npm start            # http://localhost:4200, gọi BACKEND THẬT (cần chạy Spring Boot ở cổng 8080)
npm run start:mock   # http://localhost:4200, dữ liệu GIẢ trong trình duyệt, không cần backend
```

| Lệnh | Dữ liệu | Tài khoản |
|---|---|---|
| `npm start` | Backend thật. `ng serve` chuyển `/api/*` sang `http://localhost:8080` (xem `proxy.conf.json`) | Đăng ký mới; admin do backend tạo (`ADMIN_EMAIL` / `ADMIN_PASSWORD`, profile `dev`) |
| `npm run start:mock` | localStorage. Muốn làm lại từ đầu: menu tài khoản → "Đặt lại dữ liệu mẫu" | `nam@gmail.com` / `12345678` (học viên), `admin@cassin.local` / `admin-dev-123` (quản trị) |

Đổi giữa hai chế độ KHÔNG cần sửa code: `start:mock` thay `src/environments/environment.ts`
bằng `environment.mock.ts` lúc build (xem `fileReplacements` trong `angular.json`).
Dữ liệu mẫu của mock (`core/mock/mock-seed.ts`) và migration `V9__seed_content.sql` ở backend là CÙNG một nội dung.

## Cấu trúc

```
src/app/
├── core/                  Không có giao diện, dùng chung cả app
│   ├── models/            Kiểu dữ liệu khớp 1-1 với DTO của backend
│   ├── api/               "Hợp đồng" API (abstract class) + bản HTTP thật (api/http)
│   ├── mock/              Bản giả lập API: cùng luật, cùng mã lỗi với backend
│   ├── auth/              Phiên đăng nhập, guard, interceptor (gắn token, xử lý 401)
│   ├── state/             Dữ liệu dùng chung nhiều trang (số thẻ đến hạn)
│   ├── ui/                ToastService
│   ├── util/              Ngày tháng, loại từ, chuỗi
│   └── config/            Cấu hình chạy (mock / thật lấy từ src/environments/)
├── shared/ui/             Component giao diện dùng lại: icon, modal, xác nhận, skeleton,
│                          trạng thái trống / lỗi, phân trang, biểu đồ hộp, dấu điểm...
├── layout/                Khung khu vực đăng nhập: sidebar, thanh dưới đáy, menu tài khoản
└── features/              Mỗi thư mục một nhóm trang
    ├── landing/           Trang giới thiệu
    ├── auth/              Đăng nhập, đăng ký
    ├── today/             Hôm nay
    ├── decks/             Bộ từ, chi tiết bộ, hộp thoại bộ / thẻ
    ├── review/            Ôn thẻ (chế độ tập trung)
    ├── grammar/           Chủ đề ngữ pháp, lý thuyết
    ├── quiz/              Làm bài, kết quả, lịch sử
    ├── account/           Tài khoản: sửa hồ sơ, đổi mật khẩu
    ├── admin/             Khu vực quản trị (chỉ ADMIN): tổng quan, bộ từ hệ thống, ngữ pháp + câu hỏi
    └── not-found/
```

Quy ước: mỗi component tách riêng `.ts` / `.html` / `.scss`; component dùng signal + `ChangeDetectionStrategy.OnPush`;
dữ liệu tải bằng `rxResource`, mỗi khối tự có trạng thái đang tải / trống / lỗi.

# Cassin – Frontend (Angular 22)

## Chạy

```bash
npm install
npm start            # http://localhost:4200
```

Mặc định chạy bằng **dữ liệu giả (mock)**, không cần backend. Tài khoản có sẵn: `nam@gmail.com` / `12345678`.
Dữ liệu mock lưu trong localStorage; muốn làm lại từ đầu: menu tài khoản → "Đặt lại dữ liệu mẫu".

Nối backend thật: sửa `useMockApi: false` trong `src/app/core/config/app-env.ts`, chạy backend ở cổng 8080.
`ng serve` tự chuyển `/api/*` sang `http://localhost:8080` (xem `proxy.conf.json`).

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
│   └── config/            Cấu hình chạy (mock / thật)
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
    └── not-found/
```

Quy ước: mỗi component tách riêng `.ts` / `.html` / `.scss`; component dùng signal + `ChangeDetectionStrategy.OnPush`;
dữ liệu tải bằng `rxResource`, mỗi khối tự có trạng thái đang tải / trống / lỗi.

/**
 * Cấu hình MẶC ĐỊNH: gọi backend thật (Spring Boot ở localhost:8080, đi qua proxy.conf.json).
 * Dùng cho `npm start` và bản build production.
 *
 * Muốn làm giao diện mà không bật backend: `npm run start:mock`.
 * Lệnh đó thay file này bằng environment.mock.ts lúc build (xem "fileReplacements" trong angular.json),
 * nên KHÔNG cần sửa code, và không bao giờ lỡ commit nhầm cờ mock lên repo.
 */
export const environment = {
  useMockApi: false,
};

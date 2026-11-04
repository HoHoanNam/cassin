package com.cassin.practice.dto;

import java.time.LocalDate;

/**
 * Một dòng kết quả của native query "theo ngày".
 *
 * Interface projection: Spring tự tạo object cài đặt interface này cho mỗi dòng SQL.
 * Quy tắc ghép: getDay() ↔ cột alias "day", getTotal() ↔ "total", getCorrect() ↔ "correct".
 * → alias trong câu SQL phải TRÙNG tên getter (bỏ chữ "get", chữ đầu viết thường).
 *
 * Chỉ dùng bên trong service. Không trả thẳng ra API (service đổi sang DayStat).
 */

// [ĐỊNH NGHĨA] Interface projection (hay Interface-based Projection) là một kỹ thuật trong Spring Data JPA (Java) dùng để chỉ truy vấn và lấy về một số trường (fields) cụ thể từ cơ sở dữ liệu thay vì load toàn bộ một Entity lớn
public interface DailyCount {
  LocalDate getDay();

  Long getTotal();

  Long getCorrect();
}
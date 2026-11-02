package com.cassin.practice;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Hiện chỉ cần save(). Các truy vấn thống kê / "Sổ lỗi sai" sẽ thêm vào đây ở bước sau,
 * dùng index (user_id, reviewed_at DESC) đã tạo trong V10.
 */
public interface ReviewEventRepository extends JpaRepository<ReviewEvent, Long> {
}

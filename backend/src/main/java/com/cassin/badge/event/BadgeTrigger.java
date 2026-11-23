package com.cassin.badge.event;

/**
 * Đánh dấu một sự kiện học tập có thể làm ai đó đạt huy hiệu.
 *
 * Module khác chỉ cần: tạo record implements BadgeTrigger, rồi publisher.publishEvent(...) trong service của mình.
 * Không module nào phải import gì khác của package badge → Badge cắm thêm vào, không làm module cũ phụ thuộc ngược.
 *
 * userId: người NHẬN huy hiệu (không phải người thao tác). Vd admin xử lý báo lỗi → userId = người báo lỗi.
 */
public interface BadgeTrigger {
  Long userId();
}
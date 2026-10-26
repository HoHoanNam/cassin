package com.cassin.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Không trả thẳng Page của Spring ra JSON: cấu trúc của nó dài, lộ chi tiết nội bộ,
 * và Spring cũng cảnh báo vì nó có thể thay đổi giữa các phiên bản.
 */
public record PageResponse<T>(
    List<T> content, // Danh sách các phần tử trên trang hiện tại
    int page, // Số trang (bắt đầu từ 0)
    int size, // Số lượng phần tử trên mỗi trang
    long totalElements, // Tổng số phần tử
    int totalPages // Tổng số trang
) {
  public static <T> PageResponse<T> from(Page<T> p) {
    return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }
}

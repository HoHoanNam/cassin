package com.cassin.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ do người dùng gây ra (email trùng, thẻ đã ôn hôm nay...).
 * Mang theo mã HTTP và mã lỗi, để GlobalExceptionHandler biết trả về gì cho client.
 *
 * Kế thừa RuntimeException (unchecked): không bắt buộc khai báo "throws" ở mọi method,
 * và mặc định @Transactional sẽ rollback khi gặp loại exception này.
 */
@Getter
public class BusinessException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public BusinessException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
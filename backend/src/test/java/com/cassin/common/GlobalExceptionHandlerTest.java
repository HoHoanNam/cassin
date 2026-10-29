package com.cassin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.cassin.common.dto.ErrorResponse;

/**
 * [THÊM] Test các handler lỗi: gọi thẳng method của GlobalExceptionHandler như một class Java bình thường.
 * Không cần Spring, không cần mock: vào một exception, ra một ResponseEntity → kiểm tra mã HTTP + body.
 *
 * Điều quan trọng cần giữ: MỌI lỗi đều có cùng dạng { code, message, fieldErrors }
 * để frontend (ApiError) chỉ cần một cách đọc.
 */
class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("BusinessException: giữ nguyên mã HTTP, code và message của service")
  void business_keepsStatusAndCode() {
    BusinessException ex = new BusinessException(HttpStatus.CONFLICT, "REVIEW_NOT_ALLOWED", "Thẻ này đã được ôn hôm nay");

    ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("REVIEW_NOT_ALLOWED");
    assertThat(response.getBody().message()).isEqualTo("Thẻ này đã được ôn hôm nay");
    assertThat(response.getBody().fieldErrors()).isEmpty();
  }

  @Test
  @DisplayName("Sai kiểu tham số (/api/decks/abc): 400 INVALID_PARAMETER, nêu tên tham số")
  void typeMismatch_isBadRequest() {
    // Tham số MethodParameter = null: handler chỉ đọc ex.getName(), không cần nó.
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException("abc", Long.class, "deckId", null, null);

    ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
    assertThat(response.getBody().message()).contains("deckId");
  }

  @Test
  @DisplayName("Sai HTTP method: 405 METHOD_NOT_ALLOWED")
  void wrongMethod_isMethodNotAllowed() {
    ResponseEntity<ErrorResponse> response =
        handler.handleMethod(new HttpRequestMethodNotSupportedException("DELETE"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
  }

  @Test
  @DisplayName("Vi phạm ràng buộc DB: 409, KHÔNG lộ câu SQL / tên bảng ra ngoài")
  void dataIntegrity_hidesSqlDetails() {
    DataIntegrityViolationException ex = new DataIntegrityViolationException(
        "duplicate key value violates unique constraint \"uq_users_email\"");

    ResponseEntity<ErrorResponse> response = handler.handleDataConflict(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("DATA_CONFLICT");
    assertThat(response.getBody().message()).doesNotContain("uq_users_email", "duplicate key");
  }
}

package com.cassin.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.cassin.common.dto.ErrorResponse;

/**
 * NƠI XỬ LÝ LỖI TẬP TRUNG CHO TOÀN BỘ API.
 *
 * ── Vấn đề nó giải quyết ──
 * Không có class này, mỗi controller phải tự try/catch rồi tự dựng response lỗi:
 *
 *     try { return authService.register(request); }
 *     catch (BusinessException ex) { return ResponseEntity.status(409).body(...); }
 *
 * 20 controller là 20 chỗ lặp lại, và mỗi chỗ có thể trả lỗi một kiểu khác nhau.
 * Với class này: service cứ việc "throw", controller không cần bắt gì cả,
 * mọi lỗi đều chạy về đây và được trả cho client theo CÙNG MỘT dạng ErrorResponse.
 *
 * ── Luồng chạy khi có lỗi ──
 *
 *   Client ──POST /api/auth/register──▶ AuthController ──▶ AuthService
 *                                                              │
 *                                         throw BusinessException(409, "EMAIL_TAKEN", ...)
 *                                                              │
 *       exception bay ngược lên, đi qua AuthController (không ai bắt)
 *                                                              │
 *                                                              ▼
 *   Spring thấy exception thoát khỏi controller → tìm trong class có @RestControllerAdvice
 *   một method có @ExceptionHandler khớp với loại exception đó → handleBusiness(...)
 *                                                              │
 *                                                              ▼
 *   Client ◀── 409 { "code": "EMAIL_TAKEN", "message": "Email này đã được đăng ký", "fieldErrors": {} }
 */
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
//   @ControllerAdvice: class này "áp dụng chung" cho MỌI controller trong app
//                      (Spring tự tìm thấy nó khi khởi động, giống @Service, @Component).
//   @ResponseBody:     giá trị các method trả về được chuyển thành JSON trong body response,
//                      giống như ở @RestController.
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Lỗi NGHIỆP VỤ do chính code của mình ném ra (email trùng, thẻ đã ôn hôm nay...).
   *
   * @ExceptionHandler(BusinessException.class): "khi có BusinessException (hoặc class con của nó)
   * thoát ra khỏi controller, hãy gọi method này". Spring truyền exception đó vào tham số ex.
   *
   * ResponseEntity<ErrorResponse>: một HTTP response trọn vẹn = mã trạng thái + header + body.
   * Dùng ResponseEntity (thay vì chỉ trả ErrorResponse) vì mã trạng thái thay đổi theo từng lỗi:
   * email trùng là 409, không tìm thấy là 404... Mã đó nằm sẵn trong ex.getStatus().
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
    return ResponseEntity
        .status(ex.getStatus()) // mã HTTP lấy từ exception, vd. 409
        .body(new ErrorResponse(
            ex.getCode(),       // mã lỗi cho code giao diện đọc, vd. "EMAIL_TAKEN"
            ex.getMessage(),    // câu thông báo cho người dùng đọc (truyền vào super(message))
            Map.of()));         // lỗi nghiệp vụ không gắn với field cụ thể → map rỗng
  }

  /**
   * Lỗi ĐỊNH DẠNG dữ liệu: các annotation @NotBlank, @Email, @Size... trong DTO không thỏa.
   *
   * Exception này do SPRING ném ra, không phải code của mình: khi controller có
   * "@Valid @RequestBody RegisterRequest request", Spring chuyển JSON thành RegisterRequest,
   * chạy kiểm tra, có field sai thì ném MethodArgumentNotValidException
   * NGAY TRƯỚC KHI gọi vào method controller (nên service không bao giờ nhận dữ liệu sai).
   *
   * Kết quả trả về, ví dụ:
   *   400 {
   *     "code": "VALIDATION_ERROR",
   *     "message": "Dữ liệu không hợp lệ",
   *     "fieldErrors": { "email": "Email không hợp lệ", "password": "Mật khẩu từ 8 đến 72 ký tự" }
   *   }
   * Giao diện dùng fieldErrors để hiện lỗi ngay dưới đúng ô nhập.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    // LinkedHashMap: Map giữ đúng thứ tự thêm vào (HashMap thường thì xáo trộn thứ tự),
    // để lỗi trả về theo đúng thứ tự các field.
    Map<String, String> fieldErrors = new LinkedHashMap<>();

    // getBindingResult(): toàn bộ kết quả kiểm tra.
    // getFieldErrors(): danh sách lỗi, MỖI annotation vi phạm là MỘT FieldError.
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      // error.getField():          tên field bị lỗi, vd. "password"
      // error.getDefaultMessage(): nội dung message="..." viết trong annotation
      //
      // putIfAbsent: chỉ thêm nếu field CHƯA có trong map.
      // Một field có thể vi phạm nhiều annotation cùng lúc (password rỗng vi phạm cả @NotBlank
      // lẫn @Size) → chỉ giữ một lỗi mỗi field cho gọn, thay vì ghi đè lỗi trước bằng lỗi sau.
      fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }

    return ResponseEntity
        .badRequest() // viết tắt của .status(HttpStatus.BAD_REQUEST), tức mã 400
        .body(new ErrorResponse("VALIDATION_ERROR", "Dữ liệu không hợp lệ", fieldErrors));
  }

  /**
   * [THÊM] Body không phải JSON hợp lệ (thiếu dấu ngoặc, sai kiểu dữ liệu...).
   * Spring ném lỗi này khi không đọc nổi body, còn trước cả bước @Valid.
   * Không bắt thì client nhận một lỗi 400 mặc định của Spring, khác dạng với các lỗi còn lại.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return ResponseEntity
        .badRequest()
        .body(new ErrorResponse("MALFORMED_JSON", "Dữ liệu gửi lên không đúng định dạng JSON", Map.of()));
  }

  /**
   * [THÊM] Database từ chối vì vi phạm ràng buộc (UNIQUE, khóa ngoại, CHECK...).
   *
   * Đây là câu trả lời cho câu hỏi "hai người đăng ký cùng email ĐÚNG CÙNG LÚC":
   * cả hai cùng qua existsByEmail (lúc đó chưa ai lưu), người lưu sau bị UNIQUE trong V1 chặn,
   * Spring ném DataIntegrityViolationException. Không bắt thì người dùng nhận 500.
   *
   * Cũng xảy ra khi admin xóa câu hỏi đã có người làm (khóa ngoại "chặn xóa" trong V7).
   * Trả 409 Conflict: dữ liệu xung đột với trạng thái hiện tại của hệ thống.
   * Không trả ex.getMessage() cho client: nó chứa tên bảng, tên cột, câu SQL, không nên để lộ.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException ex) {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("DATA_CONFLICT", "Dữ liệu bị trùng hoặc đang được sử dụng", Map.of()));
  }

  // Vì sao KHÔNG có @ExceptionHandler(Exception.class) để bắt "mọi lỗi còn lại"?
  // Vì nó cũng bắt luôn những lỗi Spring đã xử lý đúng sẵn: gọi sai URL (404),
  // sai method GET/POST (405)... rồi biến tất cả thành 500. Những lỗi thật sự bất ngờ
  // cứ để Spring Boot trả 500 mặc định (không kèm stack trace) và ghi log ở server.
}

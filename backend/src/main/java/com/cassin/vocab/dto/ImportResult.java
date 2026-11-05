package com.cassin.vocab.dto;

import java.util.List;

/**
 * Báo cáo trả về sau khi nhập file CSV (cả khi xem trước lẫn khi nhập thật).
 *
 * @param imported true = đã lưu vào database. false = chỉ xem trước (dryRun) HOẶC file có lỗi.
 * @param words    số TỪ mới sẽ được (hoặc đã được) tạo. Các dòng cùng term gộp thành 1 từ.
 * @param senses   số NGHĨA tương ứng (= số dòng hợp lệ của các từ mới).
 * @param skipped  từ đã có trong sổ từ của mình → bỏ qua, không tính là lỗi.
 * @param errors   rỗng = file hợp lệ. Có lỗi thì KHÔNG lưu gì (imported luôn false).
 */
public record ImportResult(
    boolean imported,
    int words,
    int senses,
    List<String> skipped,
    List<ImportError> errors
) {
  /**
   * Một lỗi trong file.
   *
   * @param line  số dòng trong file (dòng tiêu đề là 1)
   * @param field tên cột bị lỗi, vd "definition". null = lỗi của cả từ (vd 2 nghĩa trùng nhau)
   */
  public record ImportError(long line, String field, String message) {
  }

  /** File có lỗi: báo lại toàn bộ lỗi, không lưu gì. */
  public static ImportResult failed(List<ImportError> errors) {
    return new ImportResult(false, 0, 0, List.of(), List.copyOf(errors));
  }
}

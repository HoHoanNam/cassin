package com.cassin.vocab;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Câu ví dụ có đánh dấu từ đích bằng dấu ngoặc vuông:
 *
 *   "Her English has [improved] a lot."
 *      plain  = "Her English has improved a lot."
 *      target = "improved"
 *      start  = 16 (vị trí của "improved" trong plain)
 *
 * ── Vì sao không tự dò từ trong câu bằng regex theo term? ──
 * Từ trong câu thường đã bị chia: improve → improved, child → children, go → went.
 * Dò theo term sẽ che sai chỗ ("____d") hoặc không tìm thấy. Người dùng tự đánh dấu
 * đúng dạng từ xuất hiện trong câu → Fill in blank luôn che đúng, chấm đúng.
 *
 * Câu KHÔNG có đánh dấu vẫn hợp lệ (hiển thị bình thường), chỉ không dùng được cho Fill in blank.
 * Mỗi câu tối đa MỘT đánh dấu.
 */
public record ExampleMarkup(String plain, String target, int start) {
  private static final Pattern MARK = Pattern.compile("\\[([^\\[\\]]{1,100})]");

  /** Đọc câu có đánh dấu. Không có đánh dấu → Optional.empty(). */
  public static Optional<ExampleMarkup> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    Matcher m = MARK.matcher(raw);
    if (!m.find()) {
      return Optional.empty();
    }
    String target = m.group(1).strip();
    if (target.isEmpty()) {
      return Optional.empty();
    }
    // Giữ nguyên khoảng trắng hai bên ngoặc, chỉ bỏ chính 2 ký tự [ ]
    String plain = raw.substring(0, m.start()) + m.group(1) + raw.substring(m.end());
    int start = m.start() + m.group(1).indexOf(target);
    return Optional.of(new ExampleMarkup(plain, target, start));
  }

  /**
   * Kiểm tra lúc LƯU câu ví dụ (Sense.change gọi). Sai định dạng → IllegalArgumentException,
   * service đổi thành 400 kèm câu thông báo này.
   *   - ngoặc lẻ: "Turn on the [light."
   *   - nhiều hơn một đánh dấu: "[Turn] on the [light]."
   *   - đánh dấu rỗng: "Turn on the []."
   */
  public static void validate(String raw) {
    if (raw == null || (raw.indexOf('[') < 0 && raw.indexOf(']') < 0)) {
      return;
    }
    Matcher m = MARK.matcher(raw);
    if (!m.find() || m.group(1).isBlank()) {
      throw new IllegalArgumentException("Câu ví dụ đánh dấu sai, hãy dùng dạng [từ]: " + raw);
    }
    String rest = raw.substring(0, m.start()) + raw.substring(m.end());
    if (rest.indexOf('[') >= 0 || rest.indexOf(']') >= 0) {
      throw new IllegalArgumentException("Mỗi câu ví dụ chỉ đánh dấu MỘT từ: " + raw);
    }
  }

  /** Câu để HIỂN THỊ (bỏ dấu ngoặc), dùng ở màn ôn thẻ. */
  public static String plainOf(String raw) {
    return parse(raw).map(m -> m.plain()).orElse(raw);
  }

  /** "Her English has ____ a lot." */
  public String blanked() {
    return plain.substring(0, start) + "____" + plain.substring(start + target.length());
  }
}

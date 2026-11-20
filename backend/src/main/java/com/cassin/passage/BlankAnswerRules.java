package com.cassin.passage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Luật chấm câu điền từ, Java thuần (không Spring, không JPA) → test bằng JUnit thường.
 *
 * Chấm 2 bước, giống cách chấm IELTS:
 *   1. check(...)     : đúng ĐỊNH DẠNG chưa (số từ, có số hay không). Sai → khỏi so đáp án.
 *   2. normalize(...) : đưa câu trả lời và đáp án chuẩn về cùng một dạng rồi mới so.
 */
public final class BlankAnswerRules {
  /** Chỗ trống trong đề. Câu BLANK phải có đúng MỘT chỗ. */
  public static final String BLANK_MARKER = "____";

  // 15 · 3.5 · 1,000 · 15% → đều là MỘT số.
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*%?");
  // Dấu câu ở HAI ĐẦU bị bỏ ("power." → "power"); dấu ở giữa giữ nguyên ("3.5", "well-known"). Giữ lại %.
  private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\p{Punct}&&[^%]]+|[\\p{Punct}&&[^%]]+$");
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final String[] NUMBER_WORDS = { "", "ONE", "TWO", "THREE" };

  private BlankAnswerRules() {
  }

  /** "  Solar  Power. " → "solar power". Dấu nháy cong (bàn phím điện thoại) đổi về nháy thẳng. */
  public static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    String s = raw.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"');
    s = SPACES.matcher(s.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    return EDGE_PUNCTUATION.matcher(s).replaceAll("").strip();
  }

  /** Tách theo khoảng trắng. "well-known" là MỘT từ. */
  static List<String> tokens(String normalized) {
    return normalized.isEmpty() ? List.of() : Arrays.asList(normalized.split(" "));
  }

  static boolean isNumber(String token) {
    return NUMBER.matcher(token).matches();
  }

  /**
   * Kiểm tra định dạng. Hợp lệ → null; không hợp lệ → lý do.
   *
   * @param maxWords null khi kind = NUMBER
   */
  public static RejectedReason check(AnswerKind kind, Integer maxWords, String raw) {
    List<String> tokens = tokens(normalize(raw));
    long numbers = tokens.stream().filter(BlankAnswerRules::isNumber).count();
    long words = tokens.size() - numbers;

    return switch (kind) {
      case WORDS -> numbers > 0 ? RejectedReason.NUMBER_NOT_ALLOWED
          : words > maxWords ? RejectedReason.OVER_WORD_LIMIT
              : null;
      case NUMBER -> words > 0 || numbers == 0 ? RejectedReason.NUMBER_REQUIRED
          : numbers > 1 ? RejectedReason.TOO_MANY_NUMBERS
              : null;
      case WORDS_AND_OR_NUMBER -> numbers > 1 ? RejectedReason.TOO_MANY_NUMBERS
          : words > maxWords ? RejectedReason.OVER_WORD_LIMIT
              : null;
    };
  }

  /** Dòng hướng dẫn in trên đề, đúng câu chữ quen thuộc của IELTS. */
  public static String instruction(AnswerKind kind, Integer maxWords) {
    return switch (kind) {
      case NUMBER -> "A NUMBER";
      case WORDS -> maxWords == 1 ? "ONE WORD ONLY" : "NO MORE THAN " + NUMBER_WORDS[maxWords] + " WORDS";
      case WORDS_AND_OR_NUMBER -> maxWords == 1 ? "ONE WORD AND/OR A NUMBER"
          : "NO MORE THAN " + NUMBER_WORDS[maxWords] + " WORDS AND/OR A NUMBER";
    };
  }
}

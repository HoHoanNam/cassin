package com.cassin.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cassin.scan.dto.Token;

/**
 * Tách đoạn văn thành CÂU rồi thành TỪ, nhớ vị trí từng từ trong câu.
 *
 *   "He ran.  She   smiled!"  →  câu 0: "He ran."      từ: he(0-2) ran(3-6)
 *                                câu 1: "She smiled!"  từ: she(0-3) smiled(4-10)
 *
 * Khoảng trắng trong mỗi câu được gom về một dấu cách TRƯỚC khi tách từ,
 * để vị trí từ khớp với câu ví dụ trả về.
 */
public final class Tokenizer {
  private Tokenizer() {
  }

  /**
   * Ranh giới câu: sau . ! ? (kèm dấu nháy / ngoặc đóng nếu có) rồi tới khoảng trắng, hoặc một dòng trống.
   * Xuống dòng ĐƠN không tách câu: văn bản copy từ PDF / email hay bị ngắt dòng giữa câu.
   */
  private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?][\"'”’)]?)\\s+|\\R\\s*\\R");

  /** Một từ: chữ cái, có thể nối bằng một dấu nháy (don't, teacher's). Gạch nối tách thành 2 từ. */
  private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['’][A-Za-z]+)?");

  public record Result(List<String> sentences, List<Token> tokens) {
  }

  public static Result tokenize(String text) {
    List<String> sentences = new ArrayList<>();
    List<Token> tokens = new ArrayList<>();
    for (String raw : SENTENCE_END.split(text)) {
      String sentence = raw.strip().replaceAll("\\s+", " ");
      if (sentence.isEmpty()) {
        continue;
      }
      int index = sentences.size();
      sentences.add(sentence);
      Matcher m = WORD.matcher(sentence);
      boolean first = true;
      while (m.find()) {
        String lower = m.group().toLowerCase(Locale.ROOT).replace('’', '\'');
        if (lower.endsWith("'s") && !StopWords.contains(lower)) {
          lower = lower.substring(0, lower.length() - 2); // sở hữu: teacher's → teacher
        }
        boolean upper = Character.isUpperCase(m.group().charAt(0));
        tokens.add(new Token(lower, index, m.start(), m.end(), upper, first));
        first = false;
      }
    }
    return new Result(sentences, tokens);
  }
}

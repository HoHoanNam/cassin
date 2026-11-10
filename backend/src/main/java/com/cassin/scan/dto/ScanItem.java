package com.cassin.scan.dto;

import java.util.List;

import com.cassin.scan.ScanSource;
import com.cassin.vocab.PartOfSpeech;

/**
 * Một từ (hoặc cụm) CHƯA HỌC tìm thấy trong đoạn văn.
 *
 * @param term     LIBRARY: đúng term của từ hệ thống. NEW: dạng gốc đoán bằng Lemmatizer.guess (FE cho sửa)
 * @param forms    các dạng đã gặp, viết thường, theo thứ tự xuất hiện: ["running", "ran"]
 * @param count    số lần xuất hiện
 * @param example  câu đầu tiên chứa từ, đã đánh dấu: "He was [running] late." · null nếu câu quá 500 ký tự
 * @param wordId   chỉ có với LIBRARY
 * @param senses   chỉ có với LIBRARY (để xem trước nghĩa), NEW → danh sách rỗng
 */
public record ScanItem(
    String term,
    List<String> forms,
    int count,
    String example,
    ScanSource source,
    Long wordId,
    List<SensePreview> senses) {

  /** Một nghĩa của từ hệ thống, chỉ để xem trước (không cần câu ví dụ). */
  public record SensePreview(PartOfSpeech partOfSpeech, String definition) {
  }
}

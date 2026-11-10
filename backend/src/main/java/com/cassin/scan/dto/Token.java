package com.cassin.scan.dto;

/**
 * Một từ tìm thấy trong đoạn văn.
 *
 * @param lower          viết thường, dấu ’ đổi thành ', đã bỏ đuôi sở hữu 's ("Teacher's" → "teacher")
 * @param sentenceIndex  thuộc câu thứ mấy (index trong Tokenizer.Result.sentences)
 * @param start          vị trí bắt đầu TRONG CÂU đó (tính từ 0)
 * @param end            vị trí kết thúc (không tính), như String.substring(start, end)
 * @param upper          chữ đầu viết hoa trong văn bản gốc ("London", "The")
 * @param sentenceStart  là từ đầu tiên của câu (viết hoa ở đây không nói lên điều gì)
 */
public record Token(String lower, int sentenceIndex, int start, int end, boolean upper, boolean sentenceStart) {

  /** Viết hoa ở GIỮA câu → nhiều khả năng là tên riêng (London, Anna). */
  public boolean capitalizedMidSentence() {
    return upper && !sentenceStart;
  }
}

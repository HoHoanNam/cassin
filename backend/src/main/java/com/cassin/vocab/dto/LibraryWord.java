package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.Word;
import com.cassin.vocab.dto.WordSummary.SenseBrief;

/**
 * Một từ HỆ THỐNG trong Thư viện từ, kèm trạng thái của người đang xem.
 *
 * @param inMyDecks true = đã nằm trong ít nhất một bộ CỦA TÔI (nút "Thêm vào bộ" hiện "Đã thêm")
 * @param studying  true = tôi đã học ít nhất một nghĩa của từ này (có tiến độ ôn)
 */
public record LibraryWord(
    Long id,
    String term,
    String phonetic,
    List<SenseBrief> senses,
    boolean inMyDecks,
    boolean studying) {
  public static LibraryWord from(Word word, boolean inMyDecks, boolean studying) {
    return new LibraryWord(
        word.getId(),
        word.getTerm(),
        word.getPhonetic(),
        word.getSenses().stream()
            .map(s -> new SenseBrief(s.getId(), s.getPartOfSpeech(), s.getDefinition()))
            .toList(),
        inMyDecks,
        studying);
  }
}
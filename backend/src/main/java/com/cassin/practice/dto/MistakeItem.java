package com.cassin.practice.dto;

import java.time.Instant;

import com.cassin.vocab.PartOfSpeech;

/**
 * Một dòng trong "Sổ lỗi sai": một NGHĨA mà user hay làm sai gần đây.
 *
 * @param wrongCount  số lần sai trong khoảng thời gian đang xem
 * @param lastWrongAt lần sai gần nhất
 */
public record MistakeItem(
    Long senseId,
    Long wordId,
    String term, // words.term: chữ tiếng Anh, vd "light"
    PartOfSpeech partOfSpeech, // senses.part_of_speech: vd ADJECTIVE
    String definition, // senses.definition: vd "nhẹ"
    Long wrongCount, // count(e) trả về Long, KHÔNG phải int
    Instant lastWrongAt // max(e.reviewedAt): cùng kiểu với field reviewedAt
) {
}

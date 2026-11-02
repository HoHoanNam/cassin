package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.CardProgress;
import com.cassin.vocab.PartOfSpeech;
import com.cassin.vocab.Sense;
import com.cassin.vocab.Word;

/**
 * Một thẻ trong hàng đợi ôn = MỘT nghĩa của một từ.
 *
 * @param examples    câu ví dụ đã bỏ dấu [ ] (chỉ để đọc)
 * @param otherSenses các nghĩa KHÁC của cùng từ: mặt sau thẻ hiện mờ, tránh nhầm "đúng từ, sai nghĩa"
 * @param box         hộp Leitner hiện tại (thẻ mới = 1)
 * @param isNew       true = lần đầu học nghĩa này (chưa có tiến độ)
 */
public record ReviewCardResponse(
    Long senseId,
    Long wordId,
    String term,
    String phonetic,
    PartOfSpeech partOfSpeech,
    String definition,
    List<String> examples,
    List<OtherSense> otherSenses,
    int box,
    boolean isNew
) {
  public record OtherSense(PartOfSpeech partOfSpeech, String definition) {
  }

  /** Thẻ đến hạn: đã có tiến độ. */
  public static ReviewCardResponse due(CardProgress progress) {
    return of(progress.getSense(), progress.getBox(), false);
  }

  /** Thẻ mới: chưa có tiến độ, coi như hộp 1. */
  public static ReviewCardResponse fresh(Sense sense) {
    return of(sense, CardProgress.MIN_BOX, true);
  }

  private static ReviewCardResponse of(Sense sense, int box, boolean isNew) {
    Word word = sense.getWord();
    List<OtherSense> others = word.getSenses().stream()
        .filter(s -> !s.getId().equals(sense.getId()))
        .map(s -> new OtherSense(s.getPartOfSpeech(), s.getDefinition()))
        .toList();
    return new ReviewCardResponse(sense.getId(), word.getId(), word.getTerm(), word.getPhonetic(),
        sense.getPartOfSpeech(), sense.getDefinition(), sense.plainExamples(), others, box, isNew);
  }
}

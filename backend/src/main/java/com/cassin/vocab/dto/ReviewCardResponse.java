package com.cassin.vocab.dto;

import com.cassin.vocab.Card;
import com.cassin.vocab.CardProgress;

// Một thẻ trong hàng đợi: nội dung thẻ + đang ở hộp mấy + có phải thẻ mới không.
public record ReviewCardResponse(CardResponse card, int box, boolean isNew) {
  // Thẻ đến hạn (đã có tiến độ, không phải thẻ mới).
  public static ReviewCardResponse due(CardProgress p) {
    return new ReviewCardResponse(CardResponse.from(p.getCard()), p.getBox(), false);
  }

  // Thẻ mới chưa có tiến độ; lần chấm đầu tiên nó bắt đầu từ hộp 1.
  public static ReviewCardResponse fresh(Card card) {
    return new ReviewCardResponse(CardResponse.from(card), CardProgress.MIN_BOX, true);
  }
}

package com.cassin.vocab.dto;

import java.util.Objects;

import com.cassin.vocab.Deck;
import com.cassin.vocab.DeckType;

public record DeckResponse(
  Long id, 
  String deckName, 
  String description, 
  DeckType deckType,
  long cardCount, 
  boolean editable
) {
  // [CÂU HỎI] Tại sao ở đây lại phải tạo 2 constructor? Và cách xác định editable
  // theo 2 cách khác nhau thì có ý nghĩa gì?
  //
  // [TRẢ LỜI]
  // 1. Đây KHÔNG phải 2 constructor mà là 2 "static factory method" (hàm tạo object, có tên "from").
  //    Record chỉ có MỘT constructor: new DeckResponse(id, deckName, ..., editable), Java tự sinh.
  //    Hai hàm from(...) chỉ là hai cách khác nhau để chuẩn bị đủ 6 tham số cho constructor đó.
  //    Cùng tên "from" nhưng khác kiểu tham số → Java tự chọn đúng hàm (overloading).
  //
  // 2. Cần 2 hàm vì dữ liệu đến từ 2 NGUỒN khác nhau:
  //
  //    from(Deck, cardCount, userId)  ← entity Deck đầy đủ
  //       Dùng cho 1 bộ: getDeck, createDeck, updateDeck. Service đã phải tải entity để kiểm tra
  //       quyền / sửa dữ liệu, nên có sẵn Deck; số thẻ đếm riêng bằng countByDeckId.
  //
  //    from(DeckSummary, userId)      ← 1 dòng kết quả của câu truy vấn gộp
  //       Dùng cho danh sách: listDecks. Không có entity Deck (truy vấn chỉ lấy vài cột + count),
  //       nên KHÔNG gọi được deck.isOwnedBy(...).
  //
  // 3. Hai cách tính editable là CÙNG MỘT LUẬT "bộ này của tôi?", chỉ khác dữ liệu đang có:
  //    - Có entity → dùng deck.isOwnedBy(userId): luật nằm trong entity (bộ hệ thống → false,
  //      owner null → false, so id).
  //    - Chỉ có ownerId → Objects.equals(ownerId, userId). Bộ hệ thống có ownerId = null,
  //      userId thì không bao giờ null (lấy từ token) → kết quả false, khớp với isOwnedBy.
  //    Lưu ý: luật đang được viết ở 2 nơi. Sau này nếu đổi luật (VD: ADMIN sửa được bộ hệ thống)
  //    thì phải sửa CẢ isOwnedBy LẪN dòng Objects.equals bên dưới, không thì hai API trả editable khác nhau.

  // "editable" giúp frontend biết có hiện nút Sửa/Xóa hay không, khỏi phải tự đoán luật quyền.
  public static DeckResponse from(Deck deck, long cardCount, Long userId) {
    return new DeckResponse(
      deck.getId(), 
      deck.getDeckName(), 
      deck.getDescription(),
      deck.getDeckType(), 
      cardCount, 
      deck.isOwnedBy(userId)
    );
  }

  public static DeckResponse from(DeckSummary s, Long userId) {
    return new DeckResponse(
      s.id(), 
      s.deckName(), 
      s.description(), 
      s.deckType(),
      s.cardCount(), 
      Objects.equals(s.ownerId(), userId)
    );
  }
}

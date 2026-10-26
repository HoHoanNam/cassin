package com.cassin.vocab;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cassin.vocab.dto.DeckSummary;

public interface DeckRepository extends JpaRepository<Deck, Long> {
  // [CÂU HỎI] Giải thích luồng hoạt động và ý nghĩa
  // của câu truy vấn trong phương thức findVisibleSummaries()
  //
  // [TRẢ LỜI] Mục đích: lấy cho màn "Bộ từ của bạn" danh sách bộ user được xem, kèm số thẻ mỗi bộ,
  // chỉ bằng MỘT câu SQL. Đây là JPQL: viết theo tên class/field Java (Deck, d.deckName),
  // Hibernate dịch sang SQL theo tên bảng/cột (decks, deck_name).
  //
  // DB chạy theo thứ tự FROM → WHERE → GROUP BY → SELECT → ORDER BY (không phải thứ tự viết):
  //
  // 1. FROM Deck d LEFT JOIN d.cards c
  //    Ghép mỗi bộ với từng thẻ của nó: bộ có 3 thẻ → 3 dòng; bộ 0 thẻ → 1 dòng với c = NULL.
  //    LEFT (không phải INNER) để bộ rỗng không bị mất khỏi kết quả.
  //
  //      d.id | d.deckName   | c.id
  //      1    | 1000 từ      | 10
  //      1    | 1000 từ      | 11
  //      5    | IELTS        | 20
  //      7    | Bộ mới tạo   | NULL     ← bộ chưa có thẻ
  //      9    | Bộ của B     | 30       ← user khác
  //
  // 2. WHERE: chỉ giữ bộ hệ thống HOẶC bộ của user đang đăng nhập → dòng của "Bộ của B" bị loại.
  //    (:userId lấy từ tham số @Param("userId"), tức user.id() trong token.)
  //
  // 3. GROUP BY d.id, ...: gom các dòng cùng một bộ lại thành 1 dòng.
  //    Quy tắc SQL: cột nào trong SELECT / ORDER BY mà không nằm trong hàm gộp (count)
  //    thì phải có trong GROUP BY → nên phải liệt kê đủ deckName, description, ..., createdAt.
  //
  // 4. SELECT ... count(c): đếm số c KHÁC NULL trong mỗi nhóm → bộ 1 = 2, bộ 5 = 1, bộ 7 = 0.
  //    (Nếu viết count(*) thì bộ rỗng sẽ bị đếm thành 1, sai.)
  //    "new com.cassin.vocab.dto.DeckSummary(...)": mỗi dòng kết quả được đưa thẳng vào constructor
  //    của record, không tạo entity Deck → không bị Hibernate theo dõi, nhẹ hơn.
  //    d.owner.id: Hibernate đọc thẳng cột owner_id của bảng decks, KHÔNG join sang bảng users.
  //
  // 5. ORDER BY d.deckType, d.createdAt desc: enum lưu dạng chữ, "SYSTEM_DECK" < "USER_DECK"
  //    theo bảng chữ cái → bộ hệ thống lên trước; trong mỗi loại, bộ mới tạo lên đầu.
  //
  // SQL Hibernate sinh ra (bật show-sql: true để xem) gần giống:
  //   select d.id, d.deck_name, d.description, d.deck_type, d.owner_id, count(c.id)
  //   from decks d left join cards c on c.deck_id = d.id
  //   where d.deck_type = 'SYSTEM_DECK' or d.owner_id = ?
  //   group by d.id, d.deck_name, d.description, d.deck_type, d.owner_id, d.created_at
  //   order by d.deck_type, d.created_at desc

  /**
   * Bộ hệ thống + bộ của userId, kèm số thẻ, trong MỘT câu SQL.
   * Nếu dùng findAll() rồi gọi deck.getCards().size() cho từng bộ: 1 + N câu SQL
   * và tải toàn bộ thẻ chỉ để đếm.
   *
   * "select new ...": Hibernate gọi thẳng constructor của DeckSummary, phải ghi đủ tên package.
   * "left join": bộ chưa có thẻ nào vẫn hiện, với cardCount = 0.
   */
  @Query("""
      select new com.cassin.vocab.dto.DeckSummary(
          d.id, d.deckName, d.description, d.deckType, d.owner.id, count(c))
      from Deck d left join d.cards c
      where d.deckType = com.cassin.vocab.DeckType.SYSTEM_DECK or d.owner.id = :userId
      group by d.id, d.deckName, d.description, d.deckType, d.owner.id, d.createdAt
      order by d.deckType, d.createdAt desc
      """)
  List<DeckSummary> findVisibleSummaries(@Param("userId") Long userId);
}

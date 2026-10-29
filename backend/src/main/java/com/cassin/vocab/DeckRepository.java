package com.cassin.vocab;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cassin.vocab.dto.DeckSummary;

public interface DeckRepository extends JpaRepository<Deck, Long> {
  // [CÂU HỎI] Giải thích luồng hoạt động và ý nghĩa
  // của câu truy vấn trong phương thức findVisibleSummaries()
  //
  // [TRẢ LỜI] (đã CẬP NHẬT theo câu truy vấn mới có dueCount / newCount / updatedAt)
  // Mục đích: lấy cho màn "Bộ từ của bạn" danh sách bộ user được xem, kèm 3 con số mỗi bộ,
  // chỉ bằng MỘT câu SQL. Đây là JPQL: viết theo tên class/field Java (Deck, d.deckName),
  // Hibernate dịch sang SQL theo tên bảng/cột (decks, deck_name).
  //
  // DB chạy theo thứ tự FROM → WHERE → GROUP BY → SELECT → ORDER BY (không phải thứ tự viết):
  //
  // 1. FROM Deck d LEFT JOIN d.cards c LEFT JOIN CardProgress p ON (p của CHÍNH user này)
  //    Mỗi bộ ghép với từng thẻ, rồi mỗi thẻ ghép với tiến độ của user (nếu có).
  //    Bộ 0 thẻ → 1 dòng c = NULL; thẻ chưa học → p = NULL. LEFT để không mất dòng nào.
  //    Hôm nay = 02/11:
  //
  //      d.id | d.deckName | c.id | p.id | p.dueDate
  //      1    | 1000 từ    | 10   | 501  | 01/11     ← đã học, đến hạn
  //      1    | 1000 từ    | 11   | 502  | 05/11     ← đã học, chưa đến hạn
  //      1    | 1000 từ    | 12   | NULL | NULL      ← chưa học (thẻ mới)
  //      7    | Bộ mới tạo | NULL | NULL | NULL      ← bộ chưa có thẻ
  //      9    | Bộ của B   | 30   | NULL | NULL      ← user khác
  //
  // 2. WHERE: chỉ giữ bộ hệ thống HOẶC bộ của user đang đăng nhập → dòng của "Bộ của B" bị loại.
  //    Điều kiện "p.user.id = :userId" nằm ở ON chứ không ở WHERE (xem javadoc bên dưới).
  //
  // 3. GROUP BY d.id, ...: gom các dòng cùng một bộ lại thành 1 dòng.
  //    Quy tắc SQL: cột nào trong SELECT / ORDER BY mà không nằm trong hàm gộp (count, sum)
  //    thì phải có trong GROUP BY → nên phải liệt kê đủ deckName, ..., createdAt, updatedAt.
  //
  // 4. SELECT, tính trên từng nhóm:
  //    count(c)                                   : số c KHÁC NULL → bộ 1 = 3, bộ 7 = 0
  //    sum(case p có VÀ p.dueDate <= hôm nay)     : bộ 1 = 1 (thẻ 10)          → dueCount
  //    sum(case c có VÀ p không có)               : bộ 1 = 1 (thẻ 12), bộ 7 = 0 → newCount
  //      ("c.id is not null" để bộ rỗng không bị đếm nhầm 1 thẻ mới từ dòng c = NULL.)
  //    "new com.cassin.vocab.dto.DeckSummary(...)": mỗi dòng kết quả được đưa thẳng vào constructor
  //    của record, không tạo entity Deck → không bị Hibernate theo dõi, nhẹ hơn.
  //    d.owner.id: Hibernate đọc thẳng cột owner_id của bảng decks, KHÔNG join sang bảng users.
  //
  // 5. ORDER BY d.deckType, d.createdAt desc: enum lưu dạng chữ, "SYSTEM_DECK" < "USER_DECK"
  //    theo bảng chữ cái → bộ hệ thống lên trước; trong mỗi loại, bộ mới tạo lên đầu.
  //
  // SQL Hibernate sinh ra (bật show-sql: true để xem) gần giống:
  //   select d.id, d.deck_name, d.description, d.deck_type, d.owner_id, count(c.id),
  //          sum(case when p.id is not null and p.due_date <= ? then 1 else 0 end),
  //          sum(case when c.id is not null and p.id is null then 1 else 0 end),
  //          d.updated_at
  //   from decks d
  //     left join cards c on c.deck_id = d.id
  //     left join card_progresses p on p.card_id = c.id and p.user_id = ?
  //   where d.deck_type = 'SYSTEM_DECK' or d.owner_id = ?
  //   group by d.id, d.deck_name, d.description, d.deck_type, d.owner_id, d.created_at, d.updated_at
  //   order by d.deck_type, d.created_at desc

  // [SỬA GHI CHÚ] Gộp 2 javadoc cũ/mới thành một. Javadoc cũ ("kèm số thẻ") nằm lơ lửng,
  // không đứng ngay trên method nào nên công cụ Javadoc bỏ qua, đọc dễ nhầm là mô tả câu cũ.
  /**
   * Bộ hệ thống + bộ của userId, mỗi bộ kèm 3 con số, trong MỘT câu SQL:
   *   cardCount: tổng số thẻ
   *   dueCount : thẻ user ĐÃ học và đã tới hạn ôn
   *   newCount : thẻ user CHƯA học lần nào (không có dòng progress)
   * Nếu dùng findAll() rồi đếm cho từng bộ: 1 + 3N câu SQL.
   *
   * "left join CardProgress p on ...": nối 2 entity KHÔNG có quan hệ khai báo trong Java (Hibernate 6 hỗ trợ).
   * Điều kiện "p.user.id = :userId" PHẢI nằm trong "on", không đặt trong "where":
   *   đặt trong where thì thẻ chưa học (p = null) bị loại mất → newCount luôn = 0,
   *   bộ rỗng cũng biến mất khỏi danh sách.
   * "sum(case when ... then 1 else 0 end)" = đếm các dòng thỏa điều kiện (JPQL: sum số nguyên → Long).
   * count(c) vẫn đúng vì mỗi thẻ chỉ có TỐI ĐA 1 progress của user này (unique user_id, card_id),
   *   nên join thêm p không nhân bản dòng thẻ.
   */
  @Query("""
      select new com.cassin.vocab.dto.DeckSummary(
          d.id, d.deckName, d.description, d.deckType, d.owner.id,
          count(c),
          sum(case when p.id is not null and p.dueDate <= :today then 1 else 0 end),
          sum(case when c.id is not null and p.id is null then 1 else 0 end),
          d.updatedAt)
      from Deck d
        left join d.cards c
        left join CardProgress p on p.card = c and p.user.id = :userId
      where d.deckType = com.cassin.vocab.DeckType.SYSTEM_DECK or d.owner.id = :userId
      group by d.id, d.deckName, d.description, d.deckType, d.owner.id, d.createdAt, d.updatedAt
      order by d.deckType, d.createdAt desc
  """)
  List<DeckSummary> findVisibleSummaries(@Param("userId") Long userId, @Param("today") LocalDate today);
}

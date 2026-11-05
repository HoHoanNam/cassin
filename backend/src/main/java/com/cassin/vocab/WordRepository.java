package com.cassin.vocab;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WordRepository extends JpaRepository<Word, Long> {

  /**
   * "Sổ từ của tôi" = 3 nguồn gộp lại:
   *   (1) từ do tôi tạo                                       w.owner.id = :userId
   *   (2) từ hệ thống nằm trong một bộ CÁ NHÂN của tôi        exists ... d.owner.id = :userId
   *   (3) từ hệ thống tôi ĐÃ BẮT ĐẦU HỌC (có tiến độ ôn)      exists CardProgress ...
   *
   * :q = "" thì khớp mọi từ (like '%%'). Dùng chuỗi rỗng thay cho null: tham số null trong
   * "(:q is null or ...)" dễ gặp lỗi "could not determine data type of parameter" ở PostgreSQL.
   *
   * Không "join fetch w.senses" vì có phân trang (Hibernate sẽ phải tải hết rồi cắt trang trong RAM).
   * Nghĩa của 20 từ trên trang được tải bằng 1 câu nhờ default_batch_fetch_size: 50 (application.yml).
   * Spring Data tự sinh câu đếm tổng (select count(w) ...) từ câu này để làm Page.
   */
  @Query("""
      select w from Word w
      where lower(w.term) like lower(concat('%', :q, '%'))
        and (w.owner.id = :userId
             or (w.owner is null and exists (
                   select 1 from Deck d join d.words dw where dw = w and d.owner.id = :userId))
             or (w.owner is null and exists (
                   select 1 from CardProgress p where p.sense.word = w and p.user.id = :userId)))
      order by w.createdAt desc, w.id desc
      """)
  Page<Word> findNotebook(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

  /** Từ trong một bộ. Quyền xem bộ được kiểm tra ở service trước khi gọi. */
  @Query("""
      select w from Deck d join d.words w
      where d.id = :deckId and lower(w.term) like lower(concat('%', :q, '%'))
      order by w.term, w.id
      """)
  Page<Word> findInDeck(@Param("deckId") Long deckId, @Param("q") String q, Pageable pageable);

  /** Chống trùng từ của một học viên: "Light" và "light" là một (khớp uq_words_owner_term trong V10). */
  boolean existsByOwnerIdAndTermIgnoreCase(Long ownerId, String term);

  /** Chống trùng từ hệ thống (khớp uq_words_system_term trong V10). */
  boolean existsByOwnerIsNullAndTermIgnoreCase(String term);

  /** Số từ hệ thống (trang thống kê admin). */
  long countByOwnerIsNull();

  /*
   * Thư viện từ: từ HỆ THỐNG khớp q, sắp A → Z.
   * onlyNew = true: bỏ những từ đã có trong sổ từ của tôi (đã nằm trong bộ của tôi, hoặc đã học).
   * Spring Data tự sinh câu đếm (select count(w) ... cùng WHERE) để làm Page.
   * 
   * ===================== Đọc câu query theo dấu ngoặc =====================
   *
   *   where  [A] w.owner is null                       → từ hệ thống (owner_id IS NULL)
   *     and  [B] lower(w.term) like '%q%'              → tìm kiếm, không phân biệt hoa thường
   *     and  [C] ( :onlyNew = false  or  ( [D] and [E] ) )
   *
   * [B] concat('%', :q, '%'): "li" → '%li%' khớp "Light", "politics"...  q = "" → '%%' khớp mọi từ
   *     (service đổi null thành "", cùng quy ước với findNotebook).
   *
   * [C] CÔNG TẮC bật/tắt bộ lọc. :onlyNew là tham số từ URL, giống nhau cho MỌI dòng:
   *       onlyNew = false → (TRUE  or ...) → luôn đúng → bộ lọc TẮT, không xét D, E
   *       onlyNew = true  → (FALSE or ...) → kết quả = (D and E) → bộ lọc BẬT
   *     Một câu query dùng cho cả 2 chế độ. boolean không bao giờ null → không bị lỗi
   *     "could not determine data type of parameter" như kiểu (:q is null or ...).
   *
   * [D] "KHÔNG có bộ nào của tôi chứa từ w"
   *       join d.words dw  → điều kiện GHÉP do Hibernate tự lấy từ @JoinTable(deck_words) trong Deck,
   *                          không phải viết ON. SQL sinh ra:
   *                            from decks d join deck_words x on x.deck_id = d.id
   *                                         join words dw on dw.id = x.word_id
   *       where dw = w     → điều kiện LỌC: từ trong bộ CHÍNH LÀ từ đang xét ở câu ngoài
   *                          (so 2 entity = so id; w lấy từ câu ngoài → "subquery tương quan")
   *         and d.owner.id = :userId → bộ đó là bộ CỦA TÔI
   *
   * [E] "Tôi CHƯA học nghĩa nào của từ w"
   *       c.sense.word = w → đi theo 2 mũi tên: tiến độ → nghĩa → từ. Hibernate tự JOIN senses:
   *                            from card_progresses c join senses s on s.id = c.sense_id
   *                            where s.word_id = w.id and c.user_id = ?
   *
   *     EXISTS dừng ngay khi thấy dòng đầu tiên; "select 1" vì EXISTS chỉ hỏi CÓ / KHÔNG.
   *     Không dùng JOIN ở câu ngoài: từ nằm trong 3 bộ sẽ bị lặp 3 dòng.
   *
   * order by w.term, w.id → SẮP XẾP (không gom nhóm, số dòng giữ nguyên; select w vẫn lấy đủ cột).
   *     w.id là cột phụ: 2 từ trùng term vẫn có thứ tự CỐ ĐỊNH → phân trang không lặp / sót từ.
   */
  @Query("""
      select w from Word w
      where w.owner is null
        and lower(w.term) like lower(concat('%', :q, '%'))
        and (:onlyNew = false or (
              not exists (select 1 from Deck d join d.words dw where dw = w and d.owner.id = :userId)
          and not exists (select 1 from CardProgress c where c.sense.word = w and c.user.id = :userId)))
      order by w.term, w.id
      """)
  Page<Word> findLibrary(
      @Param("userId") Long userId,
      @Param("q") String q,
      @Param("onlyNew") boolean onlyNew,
      Pageable pageable);

  /** Trong các từ wordIds, những từ nào đang nằm trong ít nhất một bộ CỦA TÔI. */
  @Query("""
      select distinct w.id from Deck d join d.words w
      where d.owner.id = :userId and w.id in :wordIds
      """)
  List<Long> findInOwnDecks(@Param("userId") Long userId, @Param("wordIds") Collection<Long> wordIds);

  /** Trong các từ wordIds, những từ nào tôi đã học ít nhất một nghĩa. */
  @Query("""
      select distinct p.sense.word.id from CardProgress p
      where p.user.id = :userId and p.sense.word.id in :wordIds
      """)
  List<Long> findStudiedWords(@Param("userId") Long userId, @Param("wordIds") Collection<Long> wordIds);

  /**
   * Nhập file CSV: trong các cách viết termsLower (đã viết thường), những từ nào TÔI đã có.
   * Một câu SQL cho cả file thay vì hỏi từng từ (500 dòng = 1 câu, không phải 500 câu).
   */
  @Query("""
      select lower(w.term) from Word w
      where w.owner.id = :userId and lower(w.term) in :termsLower
      """)
  List<String> findOwnTermsLower(@Param("userId") Long userId, @Param("termsLower") Collection<String> termsLower);
}

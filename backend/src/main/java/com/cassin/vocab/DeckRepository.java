package com.cassin.vocab;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.vocab.dto.DeckSummary;

public interface DeckRepository extends JpaRepository<Deck, Long> {

  /**
   * [V10] Danh sách bộ user xem được, mỗi bộ kèm 3 con số, vẫn MỘT câu SQL:
   *   wordCount: số TỪ trong bộ
   *   dueCount : số NGHĨA user đã học và đã tới hạn ôn
   *   newCount : số NGHĨA user chưa học lần nào
   *
   * Thay đổi so với bản trước: thêm một tầng join (bộ → từ → nghĩa → tiến độ).
   *
   *   FROM Deck d
   *     LEFT JOIN d.words w        mỗi bộ × mỗi từ của bộ (qua deck_words)
   *     LEFT JOIN w.senses s       mỗi từ × mỗi nghĩa
   *     LEFT JOIN CardProgress p   tiến độ của CHÍNH user này với nghĩa đó (điều kiện user nằm ở ON)
   *
   *      d.id | w.term | s.definition | p.dueDate
   *      1    | light  | ánh sáng     | 01/11      ← đến hạn
   *      1    | light  | nhẹ          | NULL       ← nghĩa mới
   *      1    | tackle | giải quyết   | 05/11      ← chưa đến hạn
   *      7    | NULL   | NULL         | NULL       ← bộ rỗng
   *
   * count(DISTINCT w): "light" xuất hiện 2 dòng (2 nghĩa) nhưng chỉ tính 1 từ → bộ 1 có 2 từ.
   * Hai cột sum(...) đếm theo DÒNG = theo nghĩa, đúng ý muốn (ôn theo từng nghĩa).
   * "s.id is not null" để bộ rỗng (dòng toàn NULL) không bị đếm nhầm 1 nghĩa mới.
   */
  @Query("""
      select new com.cassin.vocab.dto.DeckSummary(
          d.id, d.deckName, d.description, d.deckType, d.owner.id,
          count(distinct w),
          sum(case when p.id is not null and p.dueDate <= :today then 1 else 0 end),
          sum(case when s.id is not null and p.id is null then 1 else 0 end),
          d.updatedAt)
      from Deck d
        left join d.words w
        left join w.senses s
        left join CardProgress p on p.sense = s and p.user.id = :userId
      where d.deckType = com.cassin.vocab.DeckType.SYSTEM_DECK or d.owner.id = :userId
      group by d.id, d.deckName, d.description, d.deckType, d.owner.id, d.createdAt, d.updatedAt
      order by d.deckType, d.createdAt desc
      """)
  List<DeckSummary> findVisibleSummaries(@Param("userId") Long userId, @Param("today") LocalDate today);

  /** Số từ trong một bộ. */
  @Query("select count(w) from Deck d join d.words w where d.id = :deckId")
  long countWords(@Param("deckId") Long deckId);

  /** Các bộ user XEM được đang chứa từ này (trang chi tiết từ: "nằm trong bộ IELTS, Daily"). */
  @Query("""
      select d from Deck d join d.words w
      where w.id = :wordId
        and (d.deckType = com.cassin.vocab.DeckType.SYSTEM_DECK or d.owner.id = :userId)
      order by d.deckType, d.deckName
      """)
  List<Deck> findVisibleContaining(@Param("wordId") Long wordId, @Param("userId") Long userId);

  /** Các bộ HỆ THỐNG đang chứa từ này (màn admin). Tách riêng thay vì truyền userId = null vào câu trên. */
  @Query("""
      select d from Deck d join d.words w
      where w.id = :wordId and d.deckType = com.cassin.vocab.DeckType.SYSTEM_DECK
      order by d.deckName
      """)
  List<Deck> findSystemContaining(@Param("wordId") Long wordId);

  /** Id các bộ CÁ NHÂN của user đang chứa từ này (đồng bộ deckIds khi sửa từ). */
  @Query("select d.id from Deck d join d.words w where w.id = :wordId and d.owner.id = :userId")
  List<Long> findOwnDeckIdsContaining(@Param("wordId") Long wordId, @Param("userId") Long userId);

  /**
   * Thêm từ vào bộ bằng MỘT câu SQL, không tải danh sách từ của bộ lên (xem ghi chú ở Deck.words).
   * "on conflict do nothing": từ đã có trong bộ thì bỏ qua, không lỗi trùng khóa chính.
   * @Transactional: method tự khai báo có @Modifying cần transaction ghi (cùng lý do StudyDayRepository).
   */
  @Modifying
  @Transactional
  @Query(value = """
      insert into deck_words (deck_id, word_id) values (:deckId, :wordId)
      on conflict do nothing
      """, nativeQuery = true)
  void addWord(@Param("deckId") Long deckId, @Param("wordId") Long wordId);

  /** Bỏ từ khỏi bộ (từ vẫn còn trong sổ từ). Trả về số dòng đã xóa: 0 = từ vốn không có trong bộ. */
  @Modifying
  @Transactional
  @Query(value = "delete from deck_words where deck_id = :deckId and word_id = :wordId", nativeQuery = true)
  int removeWord(@Param("deckId") Long deckId, @Param("wordId") Long wordId);

  long countByDeckType(DeckType deckType);
}

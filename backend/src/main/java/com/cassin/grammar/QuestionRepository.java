package com.cassin.grammar;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {
  /**
   * Lấy ngẫu nhiên tối đa :limit câu hỏi của một chủ đề.
   *
   * nativeQuery = true: viết SQL THẬT của PostgreSQL (tên bảng, tên cột), không
   * phải JPQL.
   * Cần SQL thật vì "order by random()" là hàm riêng của PostgreSQL, JPQL không
   * có sẵn.
   * Kết quả vẫn được Hibernate đổi thành entity Question như bình thường.
   *
   * Lưu ý: order by random() xáo TOÀN BỘ câu hỏi của chủ đề rồi mới lấy 10 câu.
   * Vài trăm câu mỗi chủ đề thì không đáng kể; hàng triệu câu mới cần cách khác.
   */
  @Query(value = """
        select * from questions
        where topic_id = :topicId
        order by random()
        limit :limit
      """, nativeQuery = true)
  // [SỬA] Đổi tên findRandomById → findRandomByTopicId.
  // Tên cũ đọc như "lấy câu hỏi ngẫu nhiên theo id CÂU HỎI", trong khi tham số là
  // id CHỦ ĐỀ.
  // (Có @Query nên Spring KHÔNG đọc tên method để sinh SQL; đổi tên chỉ để đúng
  // nghĩa.)
  List<Question> findRandomByTopicId(@Param("topicId") Long topicId, @Param("limit") int limit);

  // Đếm số câu hỏi của chủ đề. Spring tự sinh: select count(*) from questions
  // where topic_id = ?
  long countByTopicId(Long topicId);

  // @EntityGraph: lấy luôn 4 đáp án trong cùng câu SQL,
  // tránh N+1 khi map sang response.
  @EntityGraph(attributePaths = "options")
  List<Question> findByTopicIdOrderById(Long topicId);

  /**
   * [THÊM] Câu hỏi đã có ai trả lời chưa? Dùng để chặn xóa (AdminGrammarService.deleteQuestion).
   * AttemptAnswer không có repository riêng nên viết JPQL ở đây (truy vấn theo entity nào cũng được).
   * "case when count(a) > 0 then true else false end": đổi số đếm thành true/false ngay trong SQL.
   */
  @Query("""
      select case when count(a) > 0 then true else false end
      from AttemptAnswer a where a.question.id = :questionId
      """)
  boolean isAnswered(@Param("questionId") Long questionId);
}

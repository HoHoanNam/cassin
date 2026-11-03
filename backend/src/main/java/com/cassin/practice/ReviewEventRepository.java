package com.cassin.practice;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cassin.practice.dto.MistakeItem;

/**
 * Ghi nhật ký luyện tập (save) và truy vấn "Sổ lỗi sai".
 * Các truy vấn theo user + thời gian dùng index (user_id, reviewed_at DESC) đã tạo trong V10.
 */
public interface ReviewEventRepository extends JpaRepository<ReviewEvent, Long> {

  /*
   * Sổ lỗi sai: các nghĩa user làm sai và CHƯA làm đúng lại, từ mốc `since` đến nay.
   * ===================== @Param hoạt động thế nào? =====================
   *
   * Hình dung câu query như một HÀM, :userId là THAM SỐ của hàm đó:
   *
   *   query findMistakes(:userId, :since) {
   *       ... where e.user.id = :userId and e.reviewedAt >= :since ...
   *   }                          ▲
   *                              └─ giống "a" được dùng trong thân hàm sum(int a, int b)
   *
   *   @Param("userId")   Long userId
   *          └──┬───┘         └──┬──┘
   *   ① tên CHỖ TRỐNG     ② tên BIẾN Java, nhận giá trị từ nơi GỌI method (Service)
   *     trong câu query
   *     (một chuỗi String)
   *
   * So với hàm sum:
   *   a dùng trong thân hàm         ↔  :userId dùng trong câu query
   *   int a ở chữ ký hàm            ↔  Long userId ở chữ ký method
   *   sum(3, 5): nơi gọi truyền 3   ↔  Service gọi findMistakes(1L, ...): truyền 1
   *   Java tự nối a với int a       ↔  @Param("userId") nối :userId với Long userId
   *
   * Vì sao cần @Param? Câu query là một CHUỖI String, Java không nhìn vào bên trong được,
   * nên không tự biết :userId ứng với tham số nào → @Param làm cầu nối.
   *
   * Quy tắc duy nhất: chữ trong @Param("...") phải GIỐNG HỆT chữ sau dấu ":" trong query.
   *   @Param("userId") Long abc      → vẫn chạy (tên biến Java đổi thoải mái)
   *   @Param("user_id") Long userId  → LỖI khi khởi động: không có tham số nào tên "userId"
   *
   * (Spring Boot bật cờ compile -parameters nên bỏ @Param vẫn chạy được, nhưng vẫn GIỮ @Param:
   *  không phụ thuộc cấu hình build, đổi tên biến không làm hỏng query, giống các repository khác.)
   *
   * ===================== Giá trị userId đến từ đâu? =====================
   *
   * KHÔNG phải do người dùng gõ vào. Nó lấy từ TOKEN đăng nhập (server ký, client không sửa được):
   *
   *   Token JWT ──► Controller (user.id() = 1) ──► Service ──► findMistakes(1L, since, page)
   *                                                                   │
   *                                      Long userId = 1  ◄───────────┘
   *                                           │  @Param("userId") nối
   *                                           ▼
   *                                      :userId = 1  trong câu query
   *
   * ⚠ Không bao giờ nhận userId từ request (vd GET /api/mistakes?userId=2): ai cũng đổi số được
   *   để xem dữ liệu người khác (lỗi IDOR).
   * ⚠ Không nối chuỗi "... where e.user_id = " + userId: dễ bị SQL Injection. Tham số :userId
   *   luôn được database coi là DỮ LIỆU, không bao giờ là LỆNH.
   * 
   * join e.sense s => @JoinColumn(name = "sense_id") ~ s.id = e.sense_id
   */
  @Query("""
      select new com.cassin.practice.dto.MistakeItem(
        s.id, w.id, w.term, s.partOfSpeech, s.definition,
        sum(case when e.correct = false then 1 else 0 end),
        max(case when e.correct = false then e.reviewedAt end)
      )
      from ReviewEvent e
      join e.sense s
      join s.word w
      where e.user.id = :userId
        and e.reviewedAt >= :since
      group by s.id, w.id, w.term, s.partOfSpeech, s.definition
      having max(case when e.correct = false then e.reviewedAt end) is not null
        and (max(case when e.correct = true then e.reviewedAt end) is null
          or max(case when e.correct = false then e.reviewedAt end)
          > max(case when e.correct = true then e.reviewedAt end))
      order by sum(case when e.correct = false then 1 else 0 end) desc,
        max(case when e.correct = false then e.reviewedAt end) desc
            """)
  List<MistakeItem> findMistakes(
      @Param("userId") Long userId,
      @Param("since") Instant since,
      Pageable pageable);
}

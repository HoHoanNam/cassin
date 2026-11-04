package com.cassin.practice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cassin.practice.dto.DailyCount;
import com.cassin.practice.dto.MistakeItem;
import com.cassin.practice.dto.ModeStat;

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

  /** Số câu và số câu đúng theo từng chế độ, trong khoảng [from, to). */
  @Query("""
      select new com.cassin.practice.dto.ModeStat(
        e.mode,
        count(e),
        sum(case when e.correct = true then 1 else 0 end)
      )
      from ReviewEvent e
      where e.user.id = :userId
        and e.reviewedAt >= :from
        and e.reviewedAt < :to
      group by e.mode
      order by e.mode
      """)
  List<ModeStat> statsByMode(
      @Param("userId") Long userId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /**
   * Số câu và số câu đúng theo TỪNG NGÀY (lịch Việt Nam) từ fromDate đến toDate, ĐỦ mọi ngày
   * (ngày không luyện vẫn có 1 dòng với total = 0, correct = 0).
   *
   * Vd statsByDay(1, 2026-10-28, 2026-11-03) → 7 dòng: [28/10: 0/0, ..., 02/11: 3/2, 03/11: 1/1]
   *
   * NATIVE query (nativeQuery = true) vì cần generate_series + at time zone: chỉ PostgreSQL có, JPQL không có.
   *   → viết bằng tên BẢNG / CỘT (review_events, reviewed_at), KHÔNG phải tên class / field.
   *   → Spring KHÔNG kiểm tra câu này lúc khởi động: viết sai chỉ lộ ra khi API được gọi.
   */
  /*
   * ===================== Đường đi của một lần gọi =====================
   *
   *  ① Service gọi statsByDay(1, 28/10, 03/11)  → thực chất gọi vào PROXY do Spring Data tạo
   *  ② Proxy đọc @Query, thấy nativeQuery = true → KHÔNG dịch, gửi nguyên văn
   *  ③ Hibernate gắn tham số: Long → bigint, LocalDate → date (câu lệnh và giá trị gửi TÁCH RIÊNG
   *     → giá trị luôn là DỮ LIỆU, không bao giờ là LỆNH → chống SQL Injection)
   *  ④ JDBC mượn 1 kết nối trong pool (HikariCP), gửi sang PostgreSQL
   *  ⑤ PostgreSQL kiểm tra cú pháp + tên cột, chọn cách chạy (dùng index user_id, reviewed_at)
   *  ⑥ Thực thi: FROM (sinh ngày) → LEFT JOIN → GROUP BY → SELECT → ORDER BY
   *  ⑦ Spring đổi mỗi dòng thành 1 object DailyCount, getter tra THEO TÊN CỘT:
   *       getDay() ↔ "day", getTotal() ↔ "total", getCorrect() ↔ "correct"
   *     ⚠ Thiếu alias (vd quên "as correct" → cột tên "sum") thì getter trả null, KHÔNG báo lỗi.
   *  ⑧ Service nhận List<DailyCount> (7 phần tử, đã xếp theo ngày)
   *
   * ===================== Giải thích từng phần =====================
   *
   * [1] generate_series(bắt đầu, kết thúc, bước nhảy) — giống vòng for:
   *       for (t = fromDate; t <= toDate; t = t + 1 ngày) xuất ra t      (kết thúc CÓ được tính)
   *     Bước nhảy đổi được: '2 days' → cách 1 ngày, '1 hour' → theo giờ, '1 week' → theo tuần.
   *
   * [2] as d(day) — đặt 2 tên cùng lúc: d = tên BẢNG (biệt danh, như "e"), day = tên CỘT duy nhất.
   *     Không đặt thì cột tên là "generate_series". Tên tùy chọn, đổi thì sửa mọi chỗ dùng.
   *
   * [3] Thứ tự ép kiểu:
   *       Java LocalDate → date '2026-11-02'                      (Hibernate gắn tham số)
   *       date → cast(... as timestamp) → '2026-11-02 00:00:00'   (đầu vào generate_series)
   *       d.day (timestamp) → at time zone → timestamptz          (so với reviewed_at, xem [4])
   *       d.day (timestamp) → cast(... as date) → '2026-11-02'    (đầu ra cho getDay(): LocalDate)
   *
   *     3 kiểu thời gian của PostgreSQL:
   *       date         chỉ ngày                                    2026-11-02
   *       timestamp    ngày + giờ, KHÔNG múi giờ ("giờ treo tường")  2026-11-02 00:00
   *       timestamptz  mốc TUYỆT ĐỐI trên dòng thời gian (lưu UTC)  2026-11-01 17:00+00
   *
   *     Vì sao PHẢI cast(:fromDate as timestamp)? generate_series không có bản nhận date, chỉ có bản
   *     timestamp và bản timestamptz. Đưa date vào, PostgreSQL TỰ chọn timestamptz (nửa đêm theo
   *     múi giờ SERVER) → bước at time zone [4] đổi ngược chiều → lần luyện bị dồn sang NHẦM NGÀY.
   *
   * [4] at time zone — phép đổi HAI CHIỀU, chiều nào tùy KIỂU của giá trị đứng trước:
   *       timestamp   at time zone 'Asia/Ho_Chi_Minh' → timestamptz   ("00:00 ở VN" → mốc 17:00 UTC hôm trước)
   *       timestamptz at time zone 'Asia/Ho_Chi_Minh' → timestamp     (mốc UTC → "đồng hồ VN đang chỉ mấy giờ")
   *     Ở đây d.day là timestamp → ra timestamptz, CÙNG KIỂU với cột reviewed_at → so sánh chính xác.
   *
   *       d.day                                            = 2026-11-02 00:00
   *       d.day at time zone 'Asia/Ho_Chi_Minh'            = 2026-11-01 17:00+00   (đầu ngày 02/11 ở VN)
   *       (d.day + interval '1 day') at time zone '...'    = 2026-11-02 17:00+00   (đầu ngày HÔM SAU)
   *
   *     Ngoặc ở (d.day + interval '1 day') là BẮT BUỘC: at time zone được tính TRƯỚC dấu +.
   *     interval '1 day' ở đây là ĐỘ DÀI một ngày (tính mốc cuối), không phải bước nhảy như [1].
   *     Dùng "<" mốc cuối: lần luyện đúng 03/11 00:00 thuộc ngày 03/11, không bị tính 2 lần.
   *
   * [5] Điều kiện ON, không phải WHERE: WHERE chạy SAU join, sẽ xóa các dòng NULL của ngày trống
   *     → LEFT JOIN thành vô dụng. So cột TRẦN với một khoảng (không bọc reviewed_at trong hàm)
   *     → PostgreSQL dùng được index (user_id, reviewed_at).
   *
   * [6] count(e.id), không phải count(*): ngày trống có 1 dòng NULL do LEFT JOIN tạo ra.
   *       count(*)       đếm số DÒNG              → ngày trống ra 1 (sai)
   *       count(cột)     đếm số dòng cột KHÁC NULL → count(e.id) ra 0 (đúng)
   *                      ⚠ count(e.correct) đếm cả true lẫn false → luôn = total, KHÔNG phải số câu đúng
   *       sum(case ...)  đếm số dòng thỏa ĐIỀU KIỆN → số câu đúng (dòng NULL rơi vào else 0)
   *
   * [7] group by d.day — GOM, không LỌC: các dòng cùng ngày gộp thành 1 nhóm, mỗi nhóm → 1 dòng kết quả.
   *     Cả 7 ngày đều còn (ngày trống là 1 nhóm chứa 1 dòng NULL).
   *       ON chọn LẦN LUYỆN nào được gắn vào ngày; LEFT giữ mọi NGÀY; GROUP BY gom; SELECT đếm.
   *
   * [8] order by d.day — mặc định ASC (tăng dần): 28/10 → 03/11, đúng thứ tự vẽ biểu đồ.
   */
  @Query(value = """
      select cast(d.day as date) as day,
        count(e.id) as total,
        sum(case when e.correct then 1 else 0 end) as correct
      from generate_series(cast(:fromDate as timestamp), cast(:toDate as timestamp), interval '1 day') as d(day)
      left join review_events e
        on e.user_id = :userId
        and e.reviewed_at >= d.day at time zone 'Asia/Ho_Chi_Minh'
        and e.reviewed_at < (d.day + interval '1 day') at time zone 'Asia/Ho_Chi_Minh'
      group by d.day
      order by d.day
                  """, nativeQuery = true)
  List<DailyCount> statsByDay(
      @Param("userId") Long userId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);
}

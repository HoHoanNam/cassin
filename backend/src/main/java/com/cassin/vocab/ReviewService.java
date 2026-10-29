package com.cassin.vocab;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.BoxCount;
import com.cassin.vocab.dto.ReviewCardResponse;
import com.cassin.vocab.dto.ReviewResultResponse;
import com.cassin.vocab.dto.ReviewStatsResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {
  // Tối đa 100 thẻ đến hạn mỗi lần ôn.
  private static final int MAX_DUE_PER_QUEUE = 100;
  // Tối đa 50 thẻ mới mỗi lần ôn.
  private static final int MAX_NEW_PER_QUEUE = 50;
  // [THÊM] Đặt tên cho số 400 (trước đây viết thẳng trong getStats): đọc là hiểu, test dùng lại được.
  static final int MAX_STREAK_DAYS = 400;

  private final CardProgressRepository progressRepository;
  private final CardRepository cardRepository;
  private final UserRepository userRepository;
  private final DeckService deckService;
  private final StudyDayRepository studyDayRepository; 
  private final Clock clock;
  
  @Transactional(readOnly = true)
  public List<ReviewCardResponse> getQueue(Long deckId, int newLimit, Long userId) {
    deckService.findReadable(deckId, userId);   // bộ của người khác → 404
    LocalDate today = LocalDate.now(clock);
    
    // 1. Thẻ đến hạn trước: đây là thẻ người học SẮP QUÊN, ưu tiên hơn thẻ mới.
    List<ReviewCardResponse> queue = new ArrayList<>(
      progressRepository.findDueInDeck(userId, deckId, today, PageRequest.of(0, MAX_DUE_PER_QUEUE))
        .stream()
        .map(ReviewCardResponse::due)
        .toList()
    );

    // 2. Sau đó thêm vài thẻ mới. newLimit = 0 → chỉ ôn thẻ cũ.
    // Hàm Math.clamp() dùng để giới hạn một giá trị nằm trong một khoảng [min, max] cố định.
    int limit = Math.clamp(newLimit, 0, MAX_NEW_PER_QUEUE);
    if (limit > 0) {
      // [SỬA] Trước đây gọi findNewCards(...) nhưng BỎ QUÊN kết quả: câu SQL vẫn chạy,
      // còn danh sách thẻ mới bị vứt đi → hàng đợi không bao giờ có thẻ mới.
      // Hậu quả: thẻ mới không bao giờ được ôn → không bao giờ có CardProgress → hàng đợi luôn rỗng.
      // Java không báo lỗi khi bỏ qua giá trị trả về, nên lỗi kiểu này chỉ test mới bắt được
      // (xem ReviewServiceTest.getQueue_putsDueCardsFirst_thenNewCards).
      cardRepository.findNewCards(deckId, userId, PageRequest.of(0, limit))
          .forEach(card -> queue.add(ReviewCardResponse.fresh(card)));
    }
    return queue;
  }

  /* Chỉ thẻ đến hạn, không kèm thẻ mới: thẻ mới học theo từng bộ (trang chi tiết bộ). */
  @Transactional(readOnly = true)
  public List<ReviewCardResponse> getAllDue(Long userId) {
    LocalDate today = LocalDate.now(clock);
    List<ReviewCardResponse> queue = new ArrayList<>();
    /*
     * [SỬA nhỏ] "/**" → "/*": "/**" là Javadoc, chỉ có nghĩa khi đặt NGAY TRÊN class/method/field.
     * Đặt trong thân method thì công cụ Javadoc bỏ qua, một số IDE còn báo warning.
     *
     * PageRequest.of(0, MAX_DUE_PER_QUEUE)
     * 0: Là số trang (page index), bắt đầu tính từ 0. Do đó, 0 nghĩa là trang đầu tiên (trang số 1 ngoài đời thực). 
     * Nếu bạn truyền vào 1, đó là trang thứ hai.
     * MAX_DUE_PER_QUEUE: số lượng phần tử (item) trên mỗi trang mà bạn muốn lấy về.
     * Ý nghĩa: Lấy dữ liệu ở trang đầu tiên (0), và mỗi trang có tối đa 100 phần tử
     */
    for (CardProgress p : progressRepository.findAllDue(userId, today, PageRequest.of(0, MAX_DUE_PER_QUEUE))) {
      queue.add(ReviewCardResponse.due(p));
    }
    return queue;
  }

  @Transactional
  public ReviewResultResponse review(Long cardId, boolean remembered, Long userId) {
    Card card = cardRepository.findById(cardId)
      .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Không tìm thấy thẻ"));
    
    // Ôn được = XEM được: bộ hệ thống ai cũng ôn được (khác với sửa thẻ: chỉ chủ bộ).
    deckService.checkReadable(card.getDeck(), userId);
    
    LocalDate today = LocalDate.now(clock);

    // Lần ôn đầu tiên: chưa có tiến độ → tạo mới (chưa lưu).
    CardProgress progress = progressRepository.findByUserIdAndCardId(userId, cardId)
      .orElseGet(() -> CardProgress.start(userRepository.getReferenceById(userId), card, today));
    
    try {
      // Toàn bộ luật Leitner nằm trong entity, service không tự tính hộp.
      progress.review(remembered, today);
    } catch (IllegalStateException ex) {
      // Entity báo "đã ôn hôm nay" / "chưa đến hạn" → lỗi do người dùng → 409 kèm đúng câu đó.
      throw new BusinessException(HttpStatus.CONFLICT, "REVIEW_NOT_ALLOWED", ex.getMessage());
    }

    // Tiến độ mới → INSERT. Tiến độ cũ (Hibernate đang theo dõi) → save() không làm gì thêm,
    // dirty checking sẽ UPDATE cuối transaction. Gọi save() cho cả hai trường hợp để code gọn.
    // Lưu SAU review(): thẻ mới chỉ tốn 1 câu INSERT, thay vì INSERT rồi UPDATE.
    CardProgress saved = progressRepository.save(progress);

    // [SỬA] Trước đây markStudied nằm TRONG khối try ở trên.
    // try/catch đó chỉ dành cho lỗi "luật Leitner" của entity. Để markStudied trong đó thì
    // nếu một ngày nó ném IllegalStateException (lỗi kỹ thuật, không phải lỗi người dùng),
    // client sẽ nhận nhầm 409 "REVIEW_NOT_ALLOWED" kèm một câu thông báo kỹ thuật.
    // Đặt ở đây: chỉ đánh dấu "hôm nay có học" khi lượt ôn đã HỢP LỆ và đã lưu.
    // Cùng một transaction với save(): nếu một trong hai lỗi, cả hai cùng rollback.
    studyDayRepository.markStudied(userId, today);

    return ReviewResultResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public ReviewStatsResponse getStats(Long userId) {
    LocalDate today = LocalDate.now(clock);
    // Số lượng thẻ đến hạn hôm nay
    // [SỬA] Trước đây: countByUserIdAndDueDateLessThanEqual(userId, today), đếm MỌI progress,
    // không lọc bộ. Còn hàng đợi getAllDue (findAllDue) thì CÓ lọc bộ đọc được.
    // Hai con số lệch nhau → trang Hôm nay ghi "Bắt đầu ôn 18 thẻ" nhưng mở ra chỉ có 15.
    // Đổi sang countAllDue: cùng điều kiện WHERE với findAllDue nên hai bên luôn khớp.
    long dueToday = progressRepository.countAllDue(userId, today);
    
    // DB chỉ trả các hộp CÓ thẻ (VD: chỉ hộp 1 và 3). Biểu đồ cần đủ 5 cột,
    // nên điền 0 cho hộp trống.
    // [CÂU HỎI] Giải thích toàn bộ workflow + ý nghĩa của từng phần trong byBox và boxes
    //
    // [TRẢ LỜI] Mục tiêu: biểu đồ 5 cột trên trang Hôm nay cần ĐÚNG 5 con số, theo thứ tự hộp 1 → 5.
    // Nhưng câu GROUP BY trong countByBox chỉ trả về những hộp CÓ thẻ. Ví dụ user có thẻ ở hộp 1 và 3:
    //
    //   countByBox(userId)  →  [ BoxCount(1, 24), BoxCount(3, 40) ]      (thiếu hộp 2, 4, 5)
    //
    // Bước 1: byBox — đổi List thành Map để tra nhanh "hộp X có bao nhiêu thẻ?"
    //   Duyệt từng BoxCount, put(số hộp → số thẻ) vào HashMap.
    //   Kết quả:  { 1 → 24, 3 → 40 }
    //   (Không thể trùng key: SQL đã GROUP BY box nên mỗi hộp chỉ có một dòng.)
    //
    // Bước 2: boxes — tạo đủ 5 phần tử, hộp nào không có trong Map thì điền 0
    //   IntStream.rangeClosed(1, 5)                : sinh dãy số 1, 2, 3, 4, 5 (closed = lấy cả số 5)
    //   .mapToObj(box -> new BoxCount(...))        : mỗi số hộp → một BoxCount
    //   byBox.getOrDefault(box, 0L)                : có trong Map thì lấy số thẻ, không có thì 0
    //   .toList()                                  : gom thành List (không sửa được)
    //   Kết quả:  [ (1,24), (2,0), (3,40), (4,0), (5,0) ]
    //
    // Vì sao không dùng thẳng kết quả SQL? Frontend sẽ phải tự đoán "hộp 2 đâu rồi?" và tự điền 0.
    // Backend trả dữ liệu đủ và đúng thứ tự thì frontend chỉ việc vẽ.
    // Vì sao không chạy 5 câu count riêng cho từng hộp? 1 câu GROUP BY rẻ hơn 5 câu SQL.

    // Map<Hộp, Số lượng>
    // [SỬA] Trước đây: stream().collect(Collectors.toMap(BoxCount::box, BoxCount::total)).
    // Chạy đúng, nhưng IDE báo warning "Null type safety": trình phân tích null của Eclipse/VS Code
    // không suy ra được kiểu null của method reference (BoxCount::box) khi đi qua toMap.
    // Đổi sang vòng for (giống cách đã xử lý trong Card.cleanExamples): IDE theo dõi được từng dòng.
    Map<Integer, Long> byBox = new HashMap<>();
    for (BoxCount count : progressRepository.countByBox(userId)) {
      byBox.put(count.box(), count.total());
    }
      
    List<BoxCount> boxes = IntStream.rangeClosed(CardProgress.MIN_BOX, CardProgress.MAX_BOX)
      .mapToObj(box -> new BoxCount(box, byBox.getOrDefault(box, 0L)))
      .toList();

    // Chỉ lấy 400 ngày gần nhất (đã sắp mới → cũ): chuỗi dài hơn 400 ngày hiển thị là 400.
    // Đủ dùng, và không phải tải hết lịch sử học nhiều năm chỉ để đếm.
    int streakDays = streak(studyDayRepository.recentDays(userId, PageRequest.of(0, MAX_STREAK_DAYS)), today);
    return new ReviewStatsResponse(dueToday, boxes, streakDays);
  }

  /**
   * Đếm lùi từng ngày liên tiếp có học.
   * Bắt đầu từ HÔM NAY nếu hôm nay đã học, ngược lại từ HÔM QUA:
   * sáng nay chưa kịp ôn thì chuỗi vẫn còn, chỉ mất khi bỏ trọn một ngày.
   */
  static int streak(List<LocalDate> days, LocalDate today) {
    // [SỬA GHI CHÚ] Trước đây ghi "dùng HashSet để bỏ trùng lặp ngày". Thực ra DB không thể có
    // ngày trùng (khóa chính (user_id, study_date) trong V8). HashSet ở đây để contains() chạy O(1):
    // List.contains phải duyệt cả danh sách mỗi lần, 400 ngày x 400 lần = 160.000 phép so sánh.
    // Nhờ dùng Set, hàm cũng không phụ thuộc thứ tự của days (test truyền lộn xộn vẫn đúng).
    Set<LocalDate> set = new HashSet<>(days);
    LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
    int count = 0;
    while (set.contains(cursor)) {
      count++;
      cursor = cursor.minusDays(1);
    }
    return count;
  }
}

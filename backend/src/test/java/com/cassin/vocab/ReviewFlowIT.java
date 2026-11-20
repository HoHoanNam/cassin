package com.cassin.vocab;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.IntegrationTest;
import com.cassin.support.MutableClock;
import com.cassin.support.VocabClient;
import com.cassin.support.VocabClient.CreatedWord;

/**
 * [THÊM] Luồng học chính chạy thật trên PostgreSQL: tạo bộ → thêm từ → hàng đợi ôn → chấm Leitner → thống kê.
 *
 * Unit test (CardProgressTest, ReviewServiceTest) đã kiểm tra luật Leitner với repository giả.
 * Ở đây kiểm tra phần unit test không thấy: các câu JPQL của hàng đợi (findDueInDeck, findNewInDeck),
 * insert ... on conflict của study_days, ON DELETE CASCADE khi bỏ nghĩa, và JSON trả cho FE.
 *
 * Thời gian: MutableClock tua theo NGÀY. today() đọc cùng đồng hồ với server nên "hôm nay" luôn khớp.
 */
@IntegrationTest
class ReviewFlowIT {

  @Autowired
  MockMvc mvc;
  @Autowired
  MutableClock clock;

  ApiClient api;
  VocabClient vocab;

  @BeforeEach
  void setUp() {
    clock.reset();
    api = new ApiClient(mvc);
    vocab = new VocabClient(mvc);
  }

  @Test
  @DisplayName("Từ mới 2 nghĩa → hàng đợi có 2 thẻ mới, hộp 1, đúng thứ tự nghĩa")
  void newWord_appearsAsNewCards() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    CreatedWord word = vocab.createWord(nam, deck, "improve");

    vocab.queue(nam, deck)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].senseId").value(word.sense(0)))
        .andExpect(jsonPath("$[0].isNew").value(true))
        .andExpect(jsonPath("$[0].box").value(1))
        .andExpect(jsonPath("$[0].term").value("improve"))
        // Câu ví dụ trả về đã bỏ dấu [ ]: mặt thẻ chỉ để đọc
        .andExpect(jsonPath("$[0].examples[0]").value("We improve every day."))
        // Mặt sau hiện mờ nghĩa còn lại, tránh nhầm "đúng từ, sai nghĩa"
        .andExpect(jsonPath("$[0].otherSenses", hasSize(1)))
        .andExpect(jsonPath("$[1].senseId").value(word.sense(1)));
  }

  @Test
  @DisplayName("Hành trình Leitner: Nhớ → hộp 2, ôn lại cùng ngày / chưa đến hạn → 409, Quên → về hộp 1")
  void leitnerJourney() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    CreatedWord word = vocab.createWord(nam, deck, "improve");
    long sense = word.sense(0);

    // Ngày 0: thẻ mới → Nhớ → hộp 2, hạn +2 ngày
    vocab.review(nam, sense, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.box").value(2))
        .andExpect(jsonPath("$.dueDate").value(today().plusDays(2).toString()))
        .andExpect(jsonPath("$.reviewCount").value(1));

    // Ngày 0: bấm lại (double click, 2 tab) → 409
    vocab.review(nam, sense, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

    // Ngày 1: chưa đến hạn → 409, và KHÔNG có trong hàng đợi (chỉ còn nghĩa 2 là thẻ mới)
    clock.advance(Duration.ofDays(1));
    vocab.review(nam, sense, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
    vocab.queue(nam, deck)
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].senseId").value(word.sense(1)));

    // Ngày 2: đến hạn → đứng ĐẦU hàng đợi, trước thẻ mới
    clock.advance(Duration.ofDays(1));
    vocab.queue(nam, deck)
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].senseId").value(sense))
        .andExpect(jsonPath("$[0].isNew").value(false))
        .andExpect(jsonPath("$[0].box").value(2))
        .andExpect(jsonPath("$[1].isNew").value(true));

    // Quên → hộp 1, lapseCount +1, hạn ngày mai
    vocab.review(nam, sense, false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.box").value(1))
        .andExpect(jsonPath("$.lapseCount").value(1))
        .andExpect(jsonPath("$.reviewCount").value(2))
        .andExpect(jsonPath("$.dueDate").value(today().plusDays(1).toString()));
  }

  @Test
  @DisplayName("Thống kê: đủ 5 cột hộp, chuỗi ngày học liên tiếp, số thẻ đến hạn khớp hàng đợi")
  void stats_countBoxesAndStreak() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    CreatedWord word = vocab.createWord(nam, deck, "improve");

    vocab.review(nam, word.sense(0), true).andExpect(status().isOk()); // ngày 0: A → hộp 2, hạn ngày 2
    clock.advance(Duration.ofDays(1));
    vocab.review(nam, word.sense(1), true).andExpect(status().isOk()); // ngày 1: B → hộp 2, hạn ngày 3
    clock.advance(Duration.ofDays(1));

    // Ngày 2, chưa ôn: A đến hạn; chuỗi vẫn tính từ hôm qua (sáng nay chưa kịp ôn không mất chuỗi)
    vocab.stats(nam)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueToday").value(1))
        .andExpect(jsonPath("$.streakDays").value(2));

    vocab.review(nam, word.sense(0), true).andExpect(status().isOk()); // A → hộp 3

    vocab.stats(nam)
        .andExpect(jsonPath("$.dueToday").value(0))
        .andExpect(jsonPath("$.streakDays").value(3))
        .andExpect(jsonPath("$.boxes", hasSize(5))) // DB chỉ có hộp 2, 3 → service điền 0 cho hộp trống
        .andExpect(jsonPath("$.boxes[0].box").value(1))
        .andExpect(jsonPath("$.boxes[0].total").value(0))
        .andExpect(jsonPath("$.boxes[1].total").value(1))
        .andExpect(jsonPath("$.boxes[2].total").value(1));

    // Bỏ trọn một ngày → mất chuỗi
    clock.advance(Duration.ofDays(2));
    vocab.stats(nam).andExpect(jsonPath("$.streakDays").value(0));
  }

  @Test
  @DisplayName("Sửa từ, bỏ 1 nghĩa: nghĩa giữ lại còn nguyên hộp, tiến độ của nghĩa bị bỏ bị xóa theo")
  void editWord_dropsProgressOfRemovedSenseOnly() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    CreatedWord word = vocab.createWord(nam, deck, "improve");
    vocab.review(nam, word.sense(0), true).andExpect(status().isOk());
    vocab.review(nam, word.sense(1), true).andExpect(status().isOk());

    // Gửi lại chỉ nghĩa 0 (có id) → nghĩa 1 bị xóa (orphanRemoval) → card_progresses xóa theo (ON DELETE CASCADE)
    mvc.perform(put("/api/words/{id}", word.id())
            .header(AUTHORIZATION, nam.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"term": "improve",
                 "senses": [{"id": %d, "partOfSpeech": "VERB", "definition": "cải thiện", "examples": []}]}
                """.formatted(word.sense(0))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.senses", hasSize(1)))
        .andExpect(jsonPath("$.senses[0].id").value(word.sense(0)))
        .andExpect(jsonPath("$.senses[0].definition").value("cải thiện"));

    vocab.stats(nam)
        .andExpect(jsonPath("$.boxes[1].box").value(2))
        .andExpect(jsonPath("$.boxes[1].total").value(1)); // còn đúng 1 thẻ ở hộp 2

    vocab.review(nam, word.sense(1), true)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SENSE_NOT_FOUND"));
  }

  @Test
  @DisplayName("Ôn thẻ ghi nhật ký FLASHCARD → thống kê luyện tập thấy được")
  void review_isLoggedForPracticeStats() throws Exception {
    // (Kiểm tra lượt luyện ghi giờ theo Clock khi tua đồng hồ: xem PracticeFlowIT.reviewEvents_followAppClock)
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    CreatedWord word = vocab.createWord(nam, deck, "improve");
    vocab.review(nam, word.sense(0), true).andExpect(status().isOk());
    vocab.review(nam, word.sense(1), false).andExpect(status().isOk());
    vocab.review(nam, word.sense(1), false).andExpect(status().isConflict()); // lượt bị 409 KHÔNG được ghi

    mvc.perform(get("/api/practice/stats").param("days", "7").header(AUTHORIZATION, nam.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.correct").value(1))
        .andExpect(jsonPath("$.byMode[0].mode").value("FLASHCARD"))
        .andExpect(jsonPath("$.byMode[0].total").value(2));
  }

  private LocalDate today() {
    return LocalDate.now(clock); // cùng "hôm nay" (giờ VN) với server
  }
}

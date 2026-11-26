package com.cassin.practice;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.IntegrationTest;
import com.cassin.support.MutableClock;
import com.cassin.support.VocabClient;
import com.cassin.support.VocabClient.CreatedWord;
import com.jayway.jsonpath.JsonPath;

/**
 * [THÊM] Luyện tập theo câu ví dụ + Thống kê + Sổ lỗi sai, chạy thật trên PostgreSQL.
 *
 * Hai điều được kiểm tra ở đây mà unit test không thấy:
 *   - V15 đã đánh dấu [từ đích] cho câu seed → chế độ Điền từ có câu hỏi trên dữ liệu thật
 *   - review_events ghi giờ theo bean Clock (B1) → tua đồng hồ thì Thống kê / Sổ lỗi sai vẫn khớp
 */
@IntegrationTest
class PracticeFlowIT {

  /** Bộ hệ thống seed ở V9: 24 từ, mỗi từ 1 câu ví dụ. */
  private static final String COMMON_DECK = "1000 từ thông dụng";

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
  @DisplayName("V15: mọi câu seed của '1000 từ thông dụng' đều thành câu hỏi Điền từ")
  void fillBlank_seedDeck_everySenseIsPracticable() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = systemDeckNamed(nam, COMMON_DECK);

    mvc.perform(get("/api/practice/fill-blank")
            .param("deckIds", String.valueOf(deck))
            .param("count", "24")
            .header(AUTHORIZATION, nam.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(24))) // trước V15: 0 câu
        .andExpect(jsonPath("$[0].sentence").value(containsString("____")));
  }

  @Test
  @DisplayName("[THÊM] Gộp nhiều bộ: deckIds=A,B lấy câu của cả 2 bộ; chỉ A thì chỉ bộ A")
  void fillBlank_multipleDecks_unionOfDecks() throws Exception {
    ApiSession nam = api.newLearner();
    long deckA = vocab.createDeck(nam, "Environment");
    long deckB = vocab.createDeck(nam, "Health");
    vocab.createWord(nam, deckA, "recycle");
    vocab.createWord(nam, deckA, "pollute");
    vocab.createWord(nam, deckB, "recover");

    practice(nam, "fill-blank", "deckIds", deckA + "," + deckB, "count", "3")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
    practice(nam, "fill-blank", "deckIds", String.valueOf(deckA), "count", "2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  @DisplayName("[THÊM] 'Tất cả' = mọi bộ hệ thống + sổ từ, nghĩa trùng chỉ tính 1 lần")
  void fillBlank_all_systemDecksPlusNotebook() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "Của tôi");
    vocab.createWord(nam, deck, "recycle"); // 1 nghĩa luyện được, chỉ có trong sổ từ

    // 24 câu của bộ seed + 1 câu sổ từ
    practice(nam, "fill-blank", "all", "true", "count", "25")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(25)));
  }

  @Test
  @DisplayName("[THÊM] Availability khớp số câu thật; xin quá số đó → 400 PRACTICE_COUNT_EXCEEDS")
  void availability_matchesQuestions_andGuardsCount() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = systemDeckNamed(nam, COMMON_DECK);

    practice(nam, "availability", "deckIds", String.valueOf(deck))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fillBlank").value(24))
        .andExpect(jsonPath("$.maxQuestions").value(200));
    practice(nam, "fill-blank", "deckIds", String.valueOf(deck), "count", "25")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRACTICE_COUNT_EXCEEDS"));
    practice(nam, "fill-blank", "deckIds", String.valueOf(deck), "count", "201")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_COUNT"));
  }

  @Test
  @DisplayName("[THÊM] Bộ của người khác trong deckIds → 404, không lộ câu hỏi")
  void fillBlank_foreignDeck_isNotFound() throws Exception {
    ApiSession nam = api.newLearner();
    ApiSession other = api.newLearner();
    long mine = vocab.createDeck(nam, "Của Nam");
    long foreign = vocab.createDeck(other, "Của người khác");

    practice(nam, "fill-blank", "deckIds", mine + "," + foreign, "count", "1")
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("V15: Điền từ 'improve' chấm theo dạng đã chia trong câu ('improved')")
  void fillBlank_seedSentence_isGradedOnMarkedTarget() throws Exception {
    ApiSession nam = api.newLearner();
    long improve = librarySense(nam, "improve");

    answer(nam, improve, "FILL_BLANK", "improved")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.expected").value("improved"));

    answer(nam, improve, "FILL_BLANK", "improve") // gõ từ gốc → sai, kèm gợi ý chia dạng
        .andExpect(jsonPath("$.correct").value(false))
        .andExpect(jsonPath("$.hint").isNotEmpty());
  }

  @Test
  @DisplayName("B1: lượt luyện ghi giờ theo Clock → tua đồng hồ, Thống kê và Sổ lỗi sai vẫn đếm đúng ngày")
  void reviewEvents_followAppClock() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    CreatedWord word = vocab.createWord(nam, deck, "improve");

    clock.advance(Duration.ofDays(2));
    answer(nam, word.sense(0), "FILL_BLANK", "wrong").andExpect(jsonPath("$.correct").value(false));

    // Lượt sai nằm ở "hôm nay" theo Clock. Nếu ghi giờ máy, nó rơi về 2 ngày trước → 2 kiểm tra dưới đều sai.
    stats(nam, 1)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.daily[0].total").value(1));
    mistakes(nam, 1)
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].senseId").value(word.sense(0)))
        .andExpect(jsonPath("$[0].wrongCount").value(1));

    // 3 ngày sau: ra khỏi cửa sổ 1 ngày, vẫn còn trong cửa sổ 7 ngày
    clock.advance(Duration.ofDays(3));
    stats(nam, 1).andExpect(jsonPath("$.total").value(0));
    mistakes(nam, 1).andExpect(jsonPath("$", empty()));
    mistakes(nam, 7).andExpect(jsonPath("$", hasSize(1)));

    // Làm đúng lại → biến khỏi Sổ lỗi sai (lần sai gần nhất không còn sau lần đúng gần nhất)
    answer(nam, word.sense(0), "FILL_BLANK", "improve").andExpect(jsonPath("$.correct").value(true));
    mistakes(nam, 7).andExpect(jsonPath("$", empty()));
  }

  // ===== helper =====

  private ResultActions answer(ApiSession user, long senseId, String mode, String text) throws Exception {
    return mvc.perform(post("/api/practice/answers")
        .header(AUTHORIZATION, user.bearer())
        .contentType(APPLICATION_JSON)
        .content("""
            {"senseId": %d, "exampleIndex": 0, "mode": "%s", "answer": "%s", "durationMs": 2000}
            """.formatted(senseId, mode, text)));
  }

  /** GET /api/practice/{mode} với các cặp tham số (tên, giá trị). */
  private ResultActions practice(ApiSession user, String mode, String... params) throws Exception {
    var request = get("/api/practice/" + mode).header(AUTHORIZATION, user.bearer());
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mvc.perform(request);
  }

  private ResultActions stats(ApiSession user, int days) throws Exception {
    return mvc.perform(get("/api/practice/stats")
        .param("days", String.valueOf(days))
        .header(AUTHORIZATION, user.bearer()));
  }

  private ResultActions mistakes(ApiSession user, int days) throws Exception {
    return mvc.perform(get("/api/mistakes")
        .param("days", String.valueOf(days))
        .header(AUTHORIZATION, user.bearer()));
  }

  private long systemDeckNamed(ApiSession user, String name) throws Exception {
    MvcResult result = mvc.perform(get("/api/decks").header(AUTHORIZATION, user.bearer()))
        .andExpect(status().isOk())
        .andReturn();
    List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(),
        "$[?(@.deckType == 'SYSTEM_DECK' && @.deckName == '" + name + "')].id");
    if (ids.isEmpty()) {
      throw new IllegalStateException("Không thấy bộ hệ thống '" + name + "' trong dữ liệu seed");
    }
    return ids.get(0).longValue();
  }

  /** Nghĩa đầu tiên của một từ hệ thống, tìm qua Thư viện. */
  private long librarySense(ApiSession user, String term) throws Exception {
    MvcResult result = mvc.perform(get("/api/library")
            .param("q", term)
            .header(AUTHORIZATION, user.bearer()))
        .andExpect(status().isOk())
        .andReturn();
    List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(),
        "$.content[?(@.term == '" + term + "')].senses[0].id");
    if (ids.isEmpty()) {
      throw new IllegalStateException("Không thấy từ hệ thống '" + term + "' trong Thư viện");
    }
    return ids.get(0).longValue();
  }
}

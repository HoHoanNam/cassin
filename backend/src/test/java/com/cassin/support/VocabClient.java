package com.cassin.support;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * [THÊM] Các bước lặp lại của module vocab trong integration test: tạo bộ, tạo từ, lấy hàng đợi, chấm thẻ.
 * Tách khỏi ApiClient (auth): mỗi module một "client", file không phình to.
 */
public final class VocabClient {

  /** Từ vừa tạo: id từ + id các nghĩa theo đúng thứ tự gửi lên. */
  public record CreatedWord(long id, List<Long> senseIds) {
    public long sense(int index) {
      return senseIds.get(index);
    }
  }

  private final MockMvc mvc;

  public VocabClient(MockMvc mvc) {
    this.mvc = mvc;
  }

  /** POST /api/decks → id bộ cá nhân. */
  public long createDeck(ApiSession user, String name) throws Exception {
    MvcResult result = mvc.perform(post("/api/decks")
            .header(AUTHORIZATION, user.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"deckName": "%s"}
                """.formatted(name)))
        .andExpect(status().isCreated())
        .andReturn();
    return longAt(result, "$.id");
  }

  /** POST /api/words: từ có 2 nghĩa (VERB có câu ví dụ, NOUN không có), đặt vào bộ deckId. */
  public CreatedWord createWord(ApiSession user, long deckId, String term) throws Exception {
    MvcResult result = postWord(user, deckId, term)
        .andExpect(status().isCreated())
        .andReturn();
    List<Number> senseIds = JsonPath.read(result.getResponse().getContentAsString(), "$.senses[*].id");
    return new CreatedWord(longAt(result, "$.id"), senseIds.stream().map(Number::longValue).toList());
  }

  /** Gửi POST /api/words, để test tự kiểm tra kết quả (vd 409 WORD_EXISTS). */
  public ResultActions postWord(ApiSession user, long deckId, String term) throws Exception {
    return mvc.perform(post("/api/words")
        .header(AUTHORIZATION, user.bearer())
        .contentType(APPLICATION_JSON)
        .content(wordJson(term, deckId)));
  }

  /** GET /api/review-queue?deckId= (newLimit mặc định 10). */
  public ResultActions queue(ApiSession user, long deckId) throws Exception {
    return mvc.perform(get("/api/review-queue")
        .param("deckId", String.valueOf(deckId))
        .header(AUTHORIZATION, user.bearer()));
  }

  /** POST /api/senses/{id}/review */
  public ResultActions review(ApiSession user, long senseId, boolean remembered) throws Exception {
    return mvc.perform(post("/api/senses/{senseId}/review", senseId)
        .header(AUTHORIZATION, user.bearer())
        .contentType(APPLICATION_JSON)
        .content("""
            {"remembered": %s, "durationMs": 1500}
            """.formatted(remembered)));
  }

  /** GET /api/review/stats */
  public ResultActions stats(ApiSession user) throws Exception {
    return mvc.perform(get("/api/review/stats").header(AUTHORIZATION, user.bearer()));
  }

  /** Thân WordRequest hợp lệ: 2 nghĩa, đặt vào một bộ (deckId <= 0 → không vào bộ nào). */
  public static String wordJson(String term, long deckId) {
    return """
        {"term": "%1$s",
         "senses": [
           {"partOfSpeech": "VERB", "definition": "nghĩa động từ", "examples": ["We [%1$s] every day."]},
           {"partOfSpeech": "NOUN", "definition": "nghĩa danh từ", "examples": []}
         ],
         "deckIds": [%2$s]}
        """.formatted(term, deckId > 0 ? String.valueOf(deckId) : "");
  }

  public static long longAt(MvcResult result, String path) throws Exception {
    Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
    return value.longValue();
  }
}

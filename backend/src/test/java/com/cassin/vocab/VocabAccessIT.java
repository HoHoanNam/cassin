package com.cassin.vocab;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.IntegrationTest;
import com.cassin.support.MutableClock;
import com.cassin.support.VocabClient;
import com.cassin.support.VocabClient.CreatedWord;
import com.jayway.jsonpath.JsonPath;

/**
 * [THÊM] Ma trận quyền của module vocab, chạy qua đủ Security filter + service + DB.
 *
 *                         của mình    hệ thống              của người khác
 *   xem bộ / hàng đợi       200         200                   404 DECK_NOT_FOUND
 *   sửa bộ, thêm từ vào     200         403 DECK_READ_ONLY    404
 *   sửa từ                  200         403 WORD_READ_ONLY    404 WORD_NOT_FOUND
 *   ôn một nghĩa            200         200                   404 SENSE_NOT_FOUND
 *
 * Của người khác trả 404 (không phải 403) để không lộ "id này có tồn tại".
 * Bộ / từ hệ thống lấy từ dữ liệu seed của Flyway (V9 → V11).
 */
@IntegrationTest
class VocabAccessIT {

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
  @DisplayName("Bộ, từ, nghĩa của người khác → 404, không lộ là có tồn tại")
  void otherLearnersContent_isInvisible() throws Exception {
    ApiSession nam = api.newLearner();
    ApiSession lan = api.newLearner();
    long namDeck = vocab.createDeck(nam, "Riêng của Nam");
    CreatedWord namWord = vocab.createWord(nam, namDeck, "secret");

    mvc.perform(get("/api/decks/{id}", namDeck).header(AUTHORIZATION, lan.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
    vocab.queue(lan, namDeck)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
    mvc.perform(get("/api/words/{id}", namWord.id()).header(AUTHORIZATION, lan.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORD_NOT_FOUND"));
    vocab.review(lan, namWord.sense(0), true)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SENSE_NOT_FOUND"));

    // Lan không "mượn" được từ của Nam vào bộ của mình
    long lanDeck = vocab.createDeck(lan, "Của Lan");
    mvc.perform(post("/api/decks/{deckId}/words/{wordId}", lanDeck, namWord.id())
            .header(AUTHORIZATION, lan.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORD_NOT_FOUND"));
  }

  @Test
  @DisplayName("Bộ hệ thống: xem và ôn được, nhưng không sửa / thêm từ được (403)")
  void systemDeck_isReadOnly() throws Exception {
    ApiSession nam = api.newLearner();
    long systemDeck = firstSystemDeck(nam);

    mvc.perform(get("/api/decks/{id}", systemDeck).header(AUTHORIZATION, nam.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editable").value(false));
    vocab.queue(nam, systemDeck)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", not(empty())));

    mvc.perform(put("/api/decks/{id}", systemDeck)
            .header(AUTHORIZATION, nam.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"deckName": "Đổi tên bộ hệ thống"}
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DECK_READ_ONLY"));

    long ownDeck = vocab.createDeck(nam, "Của Nam");
    CreatedWord ownWord = vocab.createWord(nam, ownDeck, "mine");
    mvc.perform(post("/api/decks/{deckId}/words/{wordId}", systemDeck, ownWord.id())
            .header(AUTHORIZATION, nam.bearer()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DECK_READ_ONLY"));
  }

  @Test
  @DisplayName("Từ hệ thống: ôn được, đặt vào bộ của mình được, nhưng không sửa được (403)")
  void systemWord_canBeStudiedButNotEdited() throws Exception {
    ApiSession nam = api.newLearner();
    long systemDeck = firstSystemDeck(nam);
    MvcResult page = mvc.perform(get("/api/decks/{id}/words", systemDeck)
            .param("size", "1")
            .header(AUTHORIZATION, nam.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].system").value(true))
        .andReturn();
    long systemWord = VocabClient.longAt(page, "$.content[0].id");
    long systemSense = VocabClient.longAt(page, "$.content[0].senses[0].id");

    vocab.review(nam, systemSense, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.box").value(2));

    long ownDeck = vocab.createDeck(nam, "Của Nam");
    mvc.perform(post("/api/decks/{deckId}/words/{wordId}", ownDeck, systemWord)
            .header(AUTHORIZATION, nam.bearer()))
        .andExpect(status().is2xxSuccessful());

    mvc.perform(put("/api/words/{id}", systemWord)
            .header(AUTHORIZATION, nam.bearer())
            .contentType(APPLICATION_JSON)
            .content(VocabClient.wordJson("hacked", 0)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("WORD_READ_ONLY"));
  }

  @Test
  @DisplayName("Trùng từ trong sổ (không phân biệt hoa thường) → 409; người khác trùng tên thì không sao")
  void duplicateTerm_perOwner() throws Exception {
    ApiSession nam = api.newLearner();
    ApiSession lan = api.newLearner();
    long namDeck = vocab.createDeck(nam, "IELTS");
    vocab.createWord(nam, namDeck, "improve");

    vocab.postWord(nam, namDeck, "Improve")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORD_EXISTS"));

    long lanDeck = vocab.createDeck(lan, "IELTS");
    vocab.postWord(lan, lanDeck, "improve").andExpect(status().isCreated());
  }

  /** Bộ hệ thống đầu tiên mà người học thấy (dữ liệu seed). */
  private long firstSystemDeck(ApiSession user) throws Exception {
    MvcResult result = mvc.perform(get("/api/decks").header(AUTHORIZATION, user.bearer()))
        .andExpect(status().isOk())
        .andReturn();
    List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(),
        "$[?(@.deckType == 'SYSTEM_DECK')].id");
    if (ids.isEmpty()) {
      throw new IllegalStateException("Dữ liệu seed không có bộ hệ thống nào");
    }
    return ids.get(0).longValue();
  }
}

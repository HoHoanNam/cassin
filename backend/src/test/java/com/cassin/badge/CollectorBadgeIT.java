package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.BadgeClient;
import com.cassin.support.IntegrationTest;
import com.cassin.support.VocabClient;

/** Phase 5 · Nhà sưu tầm: thêm từ bằng tay và bằng file CSV đều được tính; xem trước (dryRun) thì không. */
@IntegrationTest
class CollectorBadgeIT {
  private static final String HEADER = "term,phonetic,part_of_speech,definition,example_1,example_2\n";

  @Autowired
  MockMvc mvc;

  ApiClient api;
  VocabClient vocab;
  BadgeClient badges;

  @BeforeEach
  void setUp() {
    api = new ApiClient(mvc);
    vocab = new VocabClient(mvc);
    badges = new BadgeClient(mvc);
  }

  @Test
  @DisplayName("5 từ gõ tay + 15 từ nhập CSV = 20 → Nhà sưu tầm cấp I; xem trước không tính")
  void manualAndCsvWordsCount() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    for (int i = 1; i <= 5; i++) {
      vocab.createWord(nam, deck, "manual" + i);
    }
    String csv = csv(15);

    importCsv(nam, deck, csv, true).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(false));
    assertThat(badges.progress(nam, "COLLECTOR")).isEqualTo(5);

    importCsv(nam, deck, csv, false).andExpect(status().isOk()).andExpect(jsonPath("$.words").value(15));

    assertThat(badges.progress(nam, "COLLECTOR")).isEqualTo(20);
    assertThat(badges.level(nam, "COLLECTOR")).isEqualTo(1);
    assertThat(badges.unseenCodes(nam)).containsExactly("COLLECTOR");
  }

  @Test
  @DisplayName("Nhập 120 từ một lần: nhảy thẳng cấp I + II, popup chỉ hiện cấp II")
  void bigImportJumpsLevels() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");

    importCsv(nam, deck, csv(120), false).andExpect(status().isOk());

    assertThat(badges.level(nam, "COLLECTOR")).isEqualTo(2);
    badges.unseen(nam)
        .andExpect(jsonPath("$[0].code").value("COLLECTOR"))
        .andExpect(jsonPath("$[0].level").value(2));
  }

  /** count dòng hợp lệ: word1, word2, ... mỗi từ một nghĩa. */
  private static String csv(int count) {
    StringBuilder content = new StringBuilder(HEADER);
    for (int i = 1; i <= count; i++) {
      content.append("word").append(i).append(",,NOUN,nghĩa ").append(i).append(",,\n");
    }
    return content.toString();
  }

  private ResultActions importCsv(ApiSession user, long deckId, String content, boolean dryRun) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "vocab.csv", "text/csv",
        content.getBytes(StandardCharsets.UTF_8));
    return mvc.perform(multipart("/api/decks/{deckId}/import", deckId)
        .file(file)
        .param("dryRun", String.valueOf(dryRun))
        .header(AUTHORIZATION, user.bearer()));
  }
}

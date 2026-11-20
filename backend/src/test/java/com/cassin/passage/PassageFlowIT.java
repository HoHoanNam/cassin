package com.cassin.passage;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.jayway.jsonpath.JsonPath;

/**
 * Bài đọc chạy thật: PostgreSQL + Flyway V17 + Hibernate validate + đủ filter chain bảo mật.
 *
 * Những điều unit test không thấy được:
 *   - entity (SINGLE_TABLE, @ElementCollection...) khớp V17, CHECK của V17 không chặn dữ liệu hợp lệ
 *   - PUT thay câu hỏi không đụng UNIQUE (passage_id, position) — nhờ flush giữa DELETE và INSERT
 *   - JSON lúc làm bài thật sự không chứa đáp án
 */
@IntegrationTest
class PassageFlowIT {

  private static final String ADMIN_EMAIL = "admin@it.local";
  private static final String ADMIN_PASSWORD = "admin-it-12345";

  private static final String TEXT = """
      Solar power is now a cheap alternative to coal in many countries.\\n\\n\
      In 2020, about 15% of homes in the town used solar panels on their roofs.""";

  /** 3 câu: BLANK (1 gợi ý), TFNG, MCQ. */
  private static final String PASSAGE_JSON = """
      {
        "title": "Solar power", "content": "{TEXT}", "level": "B1",
        "questions": [
          {"type": "BLANK", "prompt": "Solar power is a cheap replacement for ____.",
           "evidence": "Solar power is now a cheap alternative to coal in many countries.",
           "explanation": "alternative = replacement", "answerKind": "WORDS", "maxWords": 1,
           "acceptedAnswers": ["coal"],
           "hints": [{"questionPhrase": "replacement", "passagePhrase": "alternative"}]},
          {"type": "TFNG", "prompt": "Most homes used solar panels in 2020.",
           "evidence": "In 2020, about 15% of homes in the town used solar panels on their roofs.",
           "explanation": "Chỉ khoảng 15%", "answer": "FALSE"},
          {"type": "MCQ", "prompt": "How is solar power described?",
           "evidence": "Solar power is now a cheap alternative to coal in many countries.",
           "explanation": "cheap", "options": [
             {"content": "expensive", "correct": false}, {"content": "cheap", "correct": true},
             {"content": "dangerous", "correct": false}, {"content": "old", "correct": false}]}
        ]
      }""".replace("{TEXT}", TEXT); // không dùng formatted(): "15%" sẽ bị hiểu là ký tự định dạng

  @Autowired
  MockMvc mvc;

  ApiClient api;
  ApiSession admin;

  @BeforeEach
  void setUp() throws Exception {
    api = new ApiClient(mvc);
    admin = api.login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  @Test
  @DisplayName("Trọn vòng: soạn → publish → làm bài có gợi ý → nộp → khóa bài → lưu trữ")
  void fullFlow() throws Exception {
    // 1. Admin soạn bài nháp: học viên chưa thấy
    MvcResult created = send(admin, post("/api/admin/passages"), PASSAGE_JSON)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.questions[*].type", contains("BLANK", "TFNG", "MCQ")))
        .andReturn();
    long passageId = id(created, "$.id");
    ApiSession nam = api.newLearner();
    send(nam, post("/api/passages/" + passageId + "/attempts"), null).andExpect(status().isNotFound());

    // 2. Publish → có trong danh sách, chưa có điểm
    send(admin, patch("/api/admin/passages/" + passageId + "/publish"), null)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
    send(nam, get("/api/passages").param("level", "B1").param("size", "50"), null)
        .andExpect(jsonPath("$.content[?(@.id == " + passageId + ")].bestScore", contains(nullValue())));

    // 3. Làm bài: 201, đề KHÔNG lộ đáp án; bấm lần nữa → 200, cùng lượt
    MvcResult started = send(nam, post("/api/passages/" + passageId + "/attempts"), null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.questions", hasSize(3)))
        .andExpect(jsonPath("$.questions[0].instruction").value("ONE WORD ONLY"))
        .andExpect(jsonPath("$.questions[0].hintsRemaining").value(1))
        .andExpect(content().string(not(containsString("acceptedAnswers"))))
        .andExpect(content().string(not(containsString("\"correct\""))))
        .andExpect(content().string(not(containsString("\"evidence\""))))
        .andExpect(content().string(not(containsString("passagePhrase"))))
        .andReturn();
    long attemptId = id(started, "$.attemptId");
    long blankId = id(started, "$.questions[0].id");
    long tfngId = id(started, "$.questions[1].id");
    long mcqId = id(started, "$.questions[2].id");
    long cheapOptionId = id(started, "$.questions[2].options[1].id");
    send(nam, post("/api/passages/" + passageId + "/attempts"), null)
        .andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").value(attemptId));

    // 4. Gợi ý bằng từ đồng nghĩa, hết gợi ý → 409
    String hintUrl = "/api/reading-attempts/" + attemptId + "/questions/" + blankId + "/hint";
    send(nam, post(hintUrl), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hint.questionPhrase").value("replacement"))
        .andExpect(jsonPath("$.hint.passagePhrase").value("alternative"))
        .andExpect(jsonPath("$.hintsRemaining").value(0));
    send(nam, post(hintUrl), null).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_MORE_HINTS"));
    send(nam, get("/api/reading-attempts/" + attemptId), null)
        .andExpect(jsonPath("$.questions[0].revealedHints[0].passagePhrase").value("alternative"));

    // 5. Nộp: đủ 3 câu đúng, 2 câu đúng không cần gợi ý
    send(nam, post("/api/reading-attempts/" + attemptId + "/submit"), """
        {"answers": [
          {"questionId": %d, "text": " Coal. "},
          {"questionId": %d, "tfngAnswer": "FALSE"},
          {"questionId": %d, "optionId": %d}]}
        """.formatted(blankId, tfngId, mcqId, cheapOptionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(3))
        .andExpect(jsonPath("$.correctWithoutHints").value(2))
        .andExpect(jsonPath("$.questions[0].acceptedAnswers[0]").value("coal"))
        .andExpect(jsonPath("$.questions[1].correctTfngAnswer").value("FALSE"))
        .andExpect(jsonPath("$.questions[2].selectedOptionId").value(cheapOptionId));
    send(nam, get("/api/reading-attempts/" + attemptId + "/result"), null).andExpect(jsonPath("$.score").value(3));
    send(nam, get("/api/passages").param("size", "50"), null)
        .andExpect(jsonPath("$.content[?(@.id == " + passageId + ")].bestScore", contains(3)));

    // 6. Đã có người làm → khóa sửa / xóa; lưu trữ được → biến khỏi danh sách học viên
    send(admin, put("/api/admin/passages/" + passageId), PASSAGE_JSON)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASSAGE_LOCKED"));
    send(admin, delete("/api/admin/passages/" + passageId), null)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASSAGE_LOCKED"));
    send(admin, get("/api/admin/passages/" + passageId), null).andExpect(jsonPath("$.locked").value(true));
    send(admin, patch("/api/admin/passages/" + passageId + "/archive"), null)
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
    send(nam, post("/api/passages/" + passageId + "/attempts"), null).andExpect(status().isNotFound());
    send(nam, get("/api/reading-attempts/" + attemptId + "/result"), null).andExpect(status().isOk()); // lịch sử còn
  }

  @Test
  @DisplayName("PUT bài nháp thay toàn bộ câu hỏi trên DB thật (không đụng UNIQUE position); xóa được khi chưa ai làm")
  void replaceQuestions_thenDelete() throws Exception {
    long passageId = id(send(admin, post("/api/admin/passages"), PASSAGE_JSON).andReturn(), "$.id");

    send(admin, put("/api/admin/passages/" + passageId), PASSAGE_JSON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questions[*].position", contains(0, 1, 2)))
        .andExpect(jsonPath("$.questions[2].options", hasSize(4)));

    send(admin, delete("/api/admin/passages/" + passageId), null).andExpect(status().isNoContent());
    send(admin, get("/api/admin/passages/" + passageId), null).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Luật của entity → 400 INVALID_PASSAGE kèm số câu; học viên gọi API admin → 403")
  void validationAndSecurity() throws Exception {
    String noBlank = PASSAGE_JSON.replace("cheap replacement for ____.", "cheap replacement for coal.");
    send(admin, post("/api/admin/passages"), noBlank)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PASSAGE"))
        .andExpect(jsonPath("$.message").value(containsString("Câu 1:")));

    ApiSession nam = api.newLearner();
    send(nam, post("/api/admin/passages"), PASSAGE_JSON).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Lượt làm của người khác → 404; câu điền từ quá số từ → sai kèm lý do")
  void ownershipAndRejectedReason() throws Exception {
    long passageId = id(send(admin, post("/api/admin/passages"), PASSAGE_JSON).andReturn(), "$.id");
    send(admin, patch("/api/admin/passages/" + passageId + "/publish"), null).andExpect(status().isOk());
    ApiSession nam = api.newLearner();
    ApiSession lan = api.newLearner();
    MvcResult started = send(nam, post("/api/passages/" + passageId + "/attempts"), null).andReturn();
    long attemptId = id(started, "$.attemptId");
    long blankId = id(started, "$.questions[0].id");

    send(lan, get("/api/reading-attempts/" + attemptId), null)
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ATTEMPT_NOT_FOUND"));

    send(nam, post("/api/reading-attempts/" + attemptId + "/submit"),
        "{\"answers\": [{\"questionId\": %d, \"text\": \"black coal\"}]}".formatted(blankId))
        .andExpect(jsonPath("$.score").value(0))
        .andExpect(jsonPath("$.questions[0].rejectedReason").value("OVER_WORD_LIMIT"));
  }

  // ─────────────── helpers ───────────────

  private ResultActions send(ApiSession who,
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json)
      throws Exception {
    request.header(AUTHORIZATION, who.bearer());
    if (json != null) {
      request.contentType(APPLICATION_JSON).content(json);
    }
    return mvc.perform(request);
  }

  private static long id(MvcResult result, String path) throws Exception {
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
  }
}

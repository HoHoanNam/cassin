package com.cassin.grammar;

import java.util.ArrayList;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * Dữ liệu mẫu dùng chung cho test phần ngữ pháp + quiz.
 *
 * Vấn đề: entity tạo bằng factory method (GrammarTopic.create, Question.create) CHƯA có id,
 * vì id chỉ được DB cấp khi INSERT. Nhưng nhiều logic lại dựa vào id:
 *   - Question.findOption(optionId) tìm đáp án theo id
 *   - QuizAttempt.start(...) so id chủ đề, phát hiện câu hỏi trùng bằng id
 * Unit test không có DB → tự gán id giả bằng ReflectionTestUtils.setField(object, "tên field", giá trị).
 * (ReflectionTestUtils có sẵn trong spring-boot-starter-test; nó ghi thẳng vào field private.
 *  Chỉ dùng trong TEST, không bao giờ dùng trong code chính.)
 *
 * Quy ước id cho dễ đọc khi test hỏng:
 *   câu hỏi id = 1  → 4 đáp án id 10, 11, 12, 13
 *   câu hỏi id = 2  → 4 đáp án id 20, 21, 22, 23
 *   ... tức id đáp án = id câu hỏi * 10 + vị trí.
 */
public final class GrammarFixtures {

  private GrammarFixtures() {
  }

  public static GrammarTopic topic(long id, String title) {
    GrammarTopic topic = GrammarTopic.create(title, CefrLevel.B1, "Giải thích " + title);
    ReflectionTestUtils.setField(topic, "id", id);
    return topic;
  }

  /** 4 đáp án, đáp án ở vị trí correctIndex là đáp án đúng. */
  public static List<OptionInput> options(String prefix, int correctIndex) {
    List<OptionInput> inputs = new ArrayList<>();
    for (int i = 0; i < Question.OPTION_COUNT; i++) {
      inputs.add(new OptionInput(prefix + " " + i, i == correctIndex));
    }
    return inputs;
  }

  /** Câu hỏi đã có id, kèm 4 đáp án đã có id (id câu * 10 + vị trí). */
  public static Question question(GrammarTopic topic, long id, int correctIndex) {
    Question question = Question.create(topic, "Câu hỏi " + id, "Giải thích câu " + id,
        options("Đáp án câu " + id, correctIndex));
    ReflectionTestUtils.setField(question, "id", id);
    for (int i = 0; i < Question.OPTION_COUNT; i++) {
      // getOptions() trả list CHỈ ĐỌC, nhưng các phần tử bên trong vẫn là object thật → gán id được.
      ReflectionTestUtils.setField(question.getOptions().get(i), "id", optionId(id, i));
    }
    return question;
  }

  public static long optionId(long questionId, int position) {
    return questionId * 10 + position;
  }
}

package com.cassin.grammar;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.grammar.dto.AdminQuestionResponse;
import com.cassin.grammar.dto.QuestionRequest;
import com.cassin.grammar.dto.TopicDetailResponse;
import com.cassin.grammar.dto.TopicRequest;
import com.cassin.quiz.QuizAttemptRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminGrammarService {
  private final GrammarTopicRepository topicRepository;
  private final QuestionRepository questionRepository;
  private final GrammarService grammarService;
  // [THÊM] Kiểm tra "chủ đề đã có người làm quiz chưa" trước khi xóa (xem
  // deleteTopic).
  private final QuizAttemptRepository attemptRepository;

  // [THÊM] Mã lỗi riêng cho từng loại: trước đây lỗi của CHỦ ĐỀ (tiêu đề
  // trống...) cũng trả
  // "INVALID_QUESTION", frontend đọc code sẽ hiểu nhầm là lỗi câu hỏi.
  private static final String INVALID_TOPIC = "INVALID_TOPIC";
  private static final String INVALID_QUESTION = "INVALID_QUESTION";

  /**
   * TOPIC
   */
  @Transactional
  public TopicDetailResponse createTopic(TopicRequest r) {
    GrammarTopic topic = topicRepository.save(
        guard(INVALID_TOPIC, () -> GrammarTopic.create(r.title(), r.level(), r.explanation())));
    return TopicDetailResponse.from(topic, 0);
  }

  @Transactional
  public TopicDetailResponse updateTopic(Long topicId, TopicRequest r) {
    GrammarTopic topic = grammarService.findTopic(topicId);
    guard(INVALID_TOPIC, () -> {
      topic.update(r.title(), r.level(), r.explanation());
      return topic;
    });
    return TopicDetailResponse.from(topic, questionRepository.countByTopicId(topicId));
  }

  @Transactional
  public void deleteTopic(Long topicId) {
    GrammarTopic topic = grammarService.findTopic(topicId);
    // [THÊM] Chặn xóa chủ đề đã có người làm quiz, giống DECK_IN_USE bên
    // AdminDeckService.
    // Trước đây trông vào khóa ngoại quiz_attempts.topic_id (V7) để DB từ chối.
    // Cách đó vẫn chặn được,
    // nhưng lỗi chỉ nổ lúc COMMIT (Hibernate gom câu DELETE tới cuối transaction),
    // sau nhiều lớp bọc
    // exception, và client chỉ nhận câu chung chung "Dữ liệu bị trùng hoặc đang
    // được sử dụng".
    // Kiểm tra trước: thông báo rõ ràng, mã lỗi riêng, không phụ thuộc cách Spring
    // dịch lỗi DB.
    // (Khóa ngoại trong DB vẫn giữ làm lưới an toàn cuối cùng.)
    if (attemptRepository.existsByTopicId(topicId)) {
      throw new BusinessException(HttpStatus.CONFLICT, "TOPIC_IN_USE",
          "Chủ đề đã có người làm bài, không thể xóa");
    }
    topicRepository.delete(topic);
  }

  /**
   * QUESTION
   */
  @Transactional(readOnly = true)
  public List<AdminQuestionResponse> listQuestions(Long topicId) {
    // [SỬA] Thêm kiểm tra chủ đề tồn tại. Trước đây topicId = 999 (không có) vẫn
    // trả 200 + []
    // → admin tưởng chủ đề có thật nhưng chưa có câu hỏi. Đúng phải là 404
    // TOPIC_NOT_FOUND.
    grammarService.findTopic(topicId);
    List<Question> questions = questionRepository.findByTopicIdOrderById(topicId);
    // .map(q -> AdminQuestionResponse.from(q)) OR
    // .map(AdminQuestionResponse::from)
    return questions.stream().map(AdminQuestionResponse::from).toList();
  }

  @Transactional
  public AdminQuestionResponse addQuestion(Long topicId, QuestionRequest r) {
    // 1. Kiểm tra topic đã tồn tại hay chưa
    GrammarTopic topic = grammarService.findTopic(topicId);

    // 2. Tạo câu hỏi (entity tự kiểm tra 4 đáp án, đúng 1 đáp án đúng...)
    // [SỬA] Đưa save(...) RA NGOÀI guard. guard chỉ nên bọc đúng chỗ ENTITY kiểm
    // tra dữ liệu.
    // Bọc cả save thì một IllegalArgumentException kỹ thuật từ tầng JPA (bug của
    // mình, không phải
    // lỗi người nhập) cũng bị đổi thành 400 "INVALID_QUESTION" → che mất bug thật.
    Question q = guard(INVALID_QUESTION,
        () -> Question.create(topic, r.content(), r.explanation(), r.toInputs()));

    // save() với id tự tăng (IDENTITY) chạy INSERT ngay, cascade lưu luôn 4 đáp án
    // → các đáp án đã có id khi map sang response.
    return AdminQuestionResponse.from(questionRepository.save(q));
  }

  @Transactional
  public AdminQuestionResponse updateQuestion(Long questionId, QuestionRequest r) {
    // 1. Kiểm tra question đã tồn tại hay chưa
    Question q = findQuestion(questionId);

    // 2. Cập nhật câu hỏi
    guard(INVALID_QUESTION, () -> {
      q.update(r.content(), r.explanation(), r.toInputs());
      return q;
    });

    return AdminQuestionResponse.from(q);
  }

  // [SỬA] Thêm @Transactional (method này bị sót). Không có thì findQuestion và
  // delete chạy trong
  // 2 transaction RIÊNG: entity tìm được ở bước 1 đã "rời" Hibernate, delete phải
  // tải lại từ đầu,
  // và nếu giữa 2 bước có lỗi thì không rollback chung được. Mọi method ghi khác
  // đều có, chỉ sót ở đây.
  @Transactional
  public void deleteQuestion(Long questionId) {
    Question question = findQuestion(questionId);
    // [THÊM] Chặn xóa câu hỏi đã có người trả lời (cùng lý do với deleteTopic).
    // Câu hỏi sai nội dung thì admin SỬA (updateQuestion giữ nguyên id đáp án),
    // không cần xóa.
    if (questionRepository.isAnswered(questionId)) {
      throw new BusinessException(HttpStatus.CONFLICT, "QUESTION_IN_USE",
          "Câu hỏi đã có người trả lời, hãy sửa thay vì xóa");
    }
    questionRepository.delete(question);
  }

  /**
   * HELPERS
   */
  private Question findQuestion(Long id) {
    return questionRepository.findById(id)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Không tìm thấy câu hỏi"));
  }

  /**
   * Entity tự kiểm tra luật ("đúng 1 đáp án đúng", "không trùng nội dung"...) và
   * ném IllegalArgumentException.
   * Không bắt thì lỗi này thành 500. Đổi sang 400 kèm đúng câu thông báo,
   * cùng cách ReviewService đổi IllegalStateException → 409.
   */
  // [CÂU HỎI] CẦN GIẢI THÍCH THÊM Ý NGHĨA SỬ DỤNG + WORKFLOW
  //
  // [TRẢ LỜI]
  // ── Vấn đề ──
  // Hai lớp kiểm tra dữ liệu khác nhau:
  // (1) @Valid trên DTO : bắt lỗi ĐƠN GIẢN theo từng field (trống, quá dài, thiếu
  // đáp án) → 400 tự động.
  // (2) Entity (Question): bắt luật của CẢ NHÓM mà annotation không diễn tả được
  // ("đúng 1 đáp án đúng", "2 đáp án không trùng nhau") → ném
  // IllegalArgumentException.
  // GlobalExceptionHandler KHÔNG bắt IllegalArgumentException (cố ý: nó cũng là
  // lỗi bug thông thường),
  // nên lỗi (2) mà lọt ra ngoài sẽ thành 500 "lỗi server", trong khi thực ra là
  // admin nhập sai.
  //
  // ── guard làm gì ──
  // Nhận vào một ĐOẠN CODE (Supplier<T> = "hàm không tham số, trả về T"), chạy nó
  // trong try:
  // - chạy êm → trả nguyên kết quả (Question, GrammarTopic...)
  // - entity ném IllegalArgumentException → đổi thành BusinessException 400 + mã
  // lỗi + đúng câu thông báo
  //
  // ── Workflow, ví dụ admin gửi 2 đáp án đúng ──
  // addQuestion
  // └─ guard(INVALID_QUESTION, () -> Question.create(...))
  // └─ action.get() ← lúc này lambda mới thật sự chạy
  // └─ Question.create → validateOptions
  // └─ throw IllegalArgumentException("Câu hỏi phải có đúng 1 đáp án đúng (đang
  // có 2)")
  // └─ catch → throw BusinessException(400, "INVALID_QUESTION", "Câu hỏi phải có
  // đúng 1 ...")
  // GlobalExceptionHandler.handleBusiness
  // └─ 400 { "code": "INVALID_QUESTION", "message": "Câu hỏi phải có đúng 1 đáp
  // án đúng (đang có 2)" }
  //
  // ── Vì sao truyền LAMBDA mà không truyền thẳng kết quả? ──
  // guard(Question.create(...)) → Java chạy create(...) TRƯỚC khi vào guard → lỗi
  // nổ ngoài try, guard vô dụng.
  // guard(() -> Question.create(...)) → chỉ truyền "công thức"; guard tự quyết
  // định chạy nó BÊN TRONG try.
  //
  // ── Vì sao update phải viết "{ q.update(...); return q; }"? ──
  // update(...) trả về void, còn Supplier phải trả về một giá trị → trả lại chính
  // q cho hợp kiểu.
  //
  // [SỬA] Thêm tham số code: lỗi chủ đề → INVALID_TOPIC, lỗi câu hỏi →
  // INVALID_QUESTION.
  private static <T> T guard(String code, Supplier<T> action) {
    try {
      return action.get();
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, code, ex.getMessage());
    }
  }
}
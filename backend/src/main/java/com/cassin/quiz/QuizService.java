package com.cassin.quiz;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.grammar.GrammarService;
import com.cassin.grammar.GrammarTopic;
import com.cassin.grammar.Question;
import com.cassin.grammar.QuestionRepository;
import com.cassin.quiz.dto.QuizHistoryItem;
import com.cassin.quiz.dto.QuizResultResponse;
import com.cassin.quiz.dto.QuizStartResponse;
import com.cassin.quiz.dto.SubmitQuizRequest;
import com.cassin.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizService {
  private static final int MAX_PAGE_SIZE = 50;

  private final QuizAttemptRepository attemptRepository;
  private final QuestionRepository questionRepository;
  private final UserRepository userRepository;
  private final GrammarService grammarService;

  @Transactional
  public QuizStartResponse start(Long topicId, Long userId) {
    // 1. Tìm topic 
    GrammarTopic topic = grammarService.findTopic(topicId);

    // 2. Xáo trộn và lấy tối đa 10 câu hỏi
    // [SỬA] Đổi tên findRandomById → findRandomByTopicId (xem QuestionRepository).
    List<Question> questions = questionRepository.findRandomByTopicId(topicId, QuizAttempt.MAX_QUESTIONS);
    // Kiểm tra ở service để trả lỗi rõ ràng. Không để entity ném IllegalArgumentException
    // (sẽ thành lỗi 500 vì GlobalExceptionHandler không bắt loại đó).
    if (questions.isEmpty()) {
      throw new BusinessException(HttpStatus.CONFLICT, "TOPIC_HAS_NO_QUESTIONS", "Chủ đề này chưa có câu hỏi");
    }

    // 3. Tạo attempt
    QuizAttempt attempt = QuizAttempt.start(userRepository.getReferenceById(userId), topic, questions);

    // [SỬA] BUG NẶNG: trước đây KHÔNG gọi save(...) → lượt làm bài chỉ tồn tại trong RAM.
    //   - Không có dòng nào trong quiz_attempts / attempt_answers.
    //   - attempt.getId() = null → response trả "attemptId": null.
    //   - Client không có id để nộp bài → POST /api/quiz-attempts/null/submit → lỗi.
    // Khác với updateDeck / review(...) (entity ĐÃ CÓ trong DB, dirty checking tự UPDATE),
    // entity MỚI tạo bằng new / factory method thì Hibernate chưa biết tới, PHẢI save() để INSERT.
    // cascade = ALL trên answers: save attempt là INSERT luôn các AttemptAnswer, không cần save từng cái.
    // save() trả về chính entity đó, giờ đã có id (IDENTITY: DB cấp id ngay khi INSERT).
    QuizAttempt saved = attemptRepository.save(attempt);

    // Convert sang DTO để trả về, không public entity thẳng ra ngoài.
    return QuizStartResponse.from(saved);
  }

  @Transactional
  public QuizResultResponse submit(Long attemptId, SubmitQuizRequest request, Long userId) {
    // 1. Kiểm tra lượt làm bài và quyền sở hữu
    QuizAttempt attempt = findOwnAttempt(attemptId, userId);

    // 2. Lấy danh sách câu hỏi và đáp án đã chọn
    Map<Long, Long> selections = toSelections(request.answers());

    // 3. Nộp bài + Xử lý lỗi
    try {
      attempt.submit(selections);
    } catch (IllegalStateException ex) {
      // "Bài này đã được nộp": lỗi do trạng thái → 409
      throw new BusinessException(HttpStatus.CONFLICT, "QUIZ_ALREADY_SUBMITTED", ex.getMessage());
    } catch (IllegalArgumentException ex) {
      // Câu hỏi không có trong đề / đáp án không thuộc câu hỏi: dữ liệu gửi lên sai → 400
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", ex.getMessage());
    }

    // 4. Trả về kết quả
    return QuizResultResponse.from(attempt);
  }

  @Transactional(readOnly = true)
  public QuizResultResponse getResult(Long attemptId, Long userId) {
    QuizAttempt attempt = findOwnAttempt(attemptId, userId);

    if (!attempt.isSubmitted()) {
      throw new BusinessException(HttpStatus.CONFLICT, "QUIZ_NOT_SUBMITTED", "Bài này chưa được nộp.");
    }

    return QuizResultResponse.from(attempt);
  }

  @Transactional(readOnly = true)
  public PageResponse<QuizHistoryItem> history(int page, int size, Long userId) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    return PageResponse.from(
        attemptRepository.findByUserIdOrderByStartedAtDesc(userId, pageable)
          .map(QuizHistoryItem::from)
    );
  }

  // Lượt của người khác → 404, giống bộ từ: không để lộ là lượt đó có tồn tại.
  private QuizAttempt findOwnAttempt(Long attemptId, Long userId) {
    return attemptRepository.findById(attemptId)
      .filter(a -> Objects.equals(a.getUser().getId(), userId))
      .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND",
        "Không tìm thấy lượt làm bài"));
  }

  /**
   * List câu trả lời → Map<id câu hỏi, id đáp án> cho QuizAttempt.submit(...).
   *
   * Dùng HashMap + vòng for thay vì Collectors.toMap:
   * - toMap ném NullPointerException khi value null, mà optionId = null (bỏ trống) là hợp lệ;
   * - tự phát hiện được một câu hỏi bị gửi 2 lần để trả lỗi 400 rõ ràng.
   */
  private static Map<Long, Long> toSelections(List<SubmitQuizRequest.AnswerInput> answers) {
    Map<Long, Long> selections = new HashMap<>();

    for (SubmitQuizRequest.AnswerInput answer : answers) {
      // [THÊM] Client gửi {"answers": [null]}: @Valid bỏ qua phần tử null (không có gì để kiểm tra),
      // nên tới đây answer.questionId() sẽ nổ NullPointerException → lỗi 500. Chặn thành 400.
      if (answer == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "Câu trả lời không được null");
      }
      // Kiểm tra trùng lặp (Nếu questionId này đã tồn tại trước đó trong map thì ném lỗi)
      if (selections.containsKey(answer.questionId())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_ANSWER",
          "Câu hỏi " + answer.questionId() + " được trả lời 2 lần");
      }
      // [SỬA] Bỏ "if (answer.questionId() != null)": @NotNull trong AnswerInput đã chặn từ controller
      // (thiếu questionId → 400 trước khi vào service). Giữ lại if đó còn nguy hiểm hơn:
      // nếu sau này lỡ xóa @NotNull, câu trả lời bị LẶNG LẼ bỏ qua thay vì báo lỗi.
      // optionId được phép null = bỏ trống câu đó.
      selections.put(answer.questionId(), answer.optionId());
    }

    return selections;
  }
}


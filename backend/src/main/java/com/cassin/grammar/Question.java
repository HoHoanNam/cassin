package com.cassin.grammar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Câu hỏi trắc nghiệm ngữ pháp: đúng 4 đáp án, đúng 1 đáp án đúng.
 *
 * Question là aggregate root (gốc) của nhóm Question + QuestionOption:
 * mọi thay đổi đáp án đều đi qua đây, nên Question luôn kiểm tra được rule của cả nhóm.
 */
@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {
  // [SỬA] Thay FIRST_POSITION / LAST_POSITION bằng số lượng đáp án: vị trí luôn là 0..OPTION_COUNT-1.
  public static final int OPTION_COUNT = 4;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Owning side (Phía sở hữu): bảng questions giữ khóa ngoại topic_id trỏ sang grammar_topics.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "topic_id", nullable = false)
  private GrammarTopic topic;

  // [SỬA] Thêm columnDefinition = "TEXT" cho khớp V6 (đề bài và lời giải thích có thể dài).
  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String explanation;

  // Inverse side (Phía không sở hữu): khóa ngoại nằm ở bảng question_options.
  // cascade = ALL: lưu/xóa câu hỏi thì lưu/xóa luôn đáp án (đáp án không tồn tại độc lập).
  // orphanRemoval = true: đáp án bị bỏ khỏi list thì xóa khỏi DB.
  // [SỬA] Khởi tạo list rỗng: trước đây là null → options.add(...) nổ NullPointerException.
  // [THÊM] @OrderBy: Hibernate luôn trả đáp án theo đúng thứ tự position 0, 1, 2, 3.
  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<QuestionOption> options = new ArrayList<>();

  // [THÊM] Timestamp giống các entity khác.
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public static Question create(GrammarTopic topic, String content, String explanation, List<OptionInput> options) {
    Objects.requireNonNull(topic, "Topic không được null");
    // [THÊM] Kiểm tra đáp án TRƯỚC khi tạo object: dữ liệu sai thì không có Question nào được tạo ra.
    validateOptions(options);

    Question question = new Question();
    question.topic = topic;
    question.applyText(content, explanation);
    // [THÊM] Trước đây danh sách options nhận vào bị bỏ qua hoàn toàn → câu hỏi không có đáp án.
    // Vị trí lấy theo thứ tự trong danh sách.
    for (int position = 0; position < OPTION_COUNT; position++) {
      OptionInput input = options.get(position);
      question.options.add(new QuestionOption(question, input.content().strip(), input.correct(), position));
    }
    return question;
  }

  /**
   * [THÊM] Sửa câu hỏi. Không cho đổi topic (chuyển câu hỏi sang chủ đề khác là nghiệp vụ khác).
   *
   * Đáp án được sửa TẠI CHỖ theo vị trí, không xóa đi tạo lại: id của 4 đáp án giữ nguyên,
   * nên các bài làm cũ (lưu id đáp án người dùng đã chọn) vẫn hợp lệ.
   */
  public void update(String content, String explanation, List<OptionInput> newOptions) {
    validateOptions(newOptions);
    applyText(content, explanation);
    for (int position = 0; position < OPTION_COUNT; position++) {
      OptionInput input = newOptions.get(position);
      // Nhờ @OrderBy, phần tử thứ i trong list chính là đáp án có position = i.
      options.get(position).change(input.content().strip(), input.correct());
    }
    // Gán tay thay vì dùng @PreUpdate: nếu admin CHỈ sửa đáp án, dòng trong bảng questions
    // không đổi gì, Hibernate không chạy UPDATE cho questions → @PreUpdate sẽ không được gọi.
    this.updatedAt = Instant.now();
  }

  /**
   * [THÊM] Đáp án có id này có phải đáp án đúng không? Dùng khi chấm bài.
   *
   * Id không thuộc câu hỏi này (người dùng cố tình gửi id đáp án của câu khác) → ném lỗi,
   * không trả false. Trả false sẽ che giấu dữ liệu sai: bài làm vẫn được lưu với một đáp án
   * không liên quan. Service nên kiểm tra bằng hasOption(...) trước để trả lỗi 400 cho rõ.
   */
  public boolean isCorrectAnswer(Long optionId) {
    return findOption(optionId).isCorrect();
  }

  /**
   * [THÊM] Lấy đáp án theo id. Dùng khi chấm quiz (AttemptAnswer cần object, không chỉ id).
   * Id không thuộc câu hỏi này → ném lỗi (cùng lý do với isCorrectAnswer).
   */
  public QuestionOption findOption(Long optionId) {
    for (QuestionOption option : options) {
      if (Objects.equals(option.getId(), optionId)) {
        return option;
      }
    }
    throw new IllegalArgumentException("Đáp án " + optionId + " không thuộc câu hỏi " + id);
  }

  /** [THÊM] Đáp án có id này có thuộc câu hỏi này không? */
  public boolean hasOption(Long optionId) {
    for (QuestionOption option : options) {
      if (Objects.equals(option.getId(), optionId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * [THÊM] Tự viết getter thay cho Lombok (Lombok thấy method đã có thì không sinh nữa).
   * Trả về danh sách CHỈ ĐỌC: gọi getOptions().add(...) hay .clear() từ bên ngoài sẽ ném lỗi,
   * nên không ai phá được rule "đúng 4 đáp án" mà không đi qua update(...).
   */
  public List<QuestionOption> getOptions() {
    return Collections.unmodifiableList(options);
  }

  // [SỬA] Đổi tên apply → applyText: giờ nó chỉ xử lý đề bài và lời giải thích,
  // còn đáp án được create/update xử lý riêng.
  private void applyText(String content, String explanation) {
    this.content = requireText(content, "Đề bài");
    this.explanation = requireText(explanation, "Lời giải thích");
  }

  /** [THÊM] Rule của cả nhóm đáp án. */
  private static void validateOptions(List<OptionInput> options) {
    if (options == null || options.size() != OPTION_COUNT) {
      throw new IllegalArgumentException("Câu hỏi phải có đúng " + OPTION_COUNT + " đáp án");
    }

    int correctCount = 0;

    // "seen" = tập các nội dung đáp án ĐÃ GẶP, dùng để phát hiện đáp án trùng nhau.
    //
    // Set là một tập hợp KHÔNG BAO GIỜ chứa phần tử trùng (khác List, List chứa trùng thoải mái).
    // Khai báo kiểu Set (interface), tạo bằng HashSet (một cách cài đặt Set): giống List / ArrayList.
    //
    // Mẹo chính: seen.add(x) trả về boolean
    //   - true : x chưa có trong set → đã thêm vào
    //   - false: x ĐÃ CÓ sẵn → không thêm, và đó chính là dấu hiệu bị trùng
    // Nên "thêm" và "kiểm tra trùng" gói gọn trong MỘT lệnh.
    //
    // Ví dụ với 4 đáp án "has lived", "lived", "Has Lived", "lives":
    //   add("has lived") → true   seen = {has lived}
    //   add("lived")     → true   seen = {has lived, lived}
    //   add("has lived") → false  ← trùng ("Has Lived" đã đổi về chữ thường trước khi add)
    //
    // Vì sao không dùng List + contains()?
    //   List.contains phải so sánh lần lượt với TỪNG phần tử (n phần tử = n lần so sánh).
    //   HashSet tính hashCode() của chuỗi để nhảy thẳng tới "ngăn" chứa nó, rồi chỉ so equals()
    //   với vài phần tử trong ngăn đó → gần như tức thì dù có hàng triệu phần tử.
    //   Với 4 đáp án thì tốc độ không khác biệt; lý do chính là code nói rõ ý định:
    //   "đây là tập các giá trị không được trùng".
    Set<String> seen = new HashSet<>();
    for (OptionInput input : options) {
      if (input == null) {
        throw new IllegalArgumentException("Đáp án không được null");
      }
      String content = requireText(input.content(), "Nội dung đáp án");
      // Đổi về chữ thường TRƯỚC khi add: HashSet so sánh bằng equals(), mà "Has Lived".equals("has lived")
      // là false. Muốn coi chúng là trùng thì phải đưa về cùng một dạng.
      // Locale.ROOT: đổi chữ theo quy tắc chung, không phụ thuộc ngôn ngữ cài trên máy.
      if (!seen.add(content.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("Hai đáp án không được trùng nhau: " + content);
      }
      if (input.correct()) {
        correctCount++;
      }
    }

    if (correctCount != 1) {
      throw new IllegalArgumentException("Câu hỏi phải có đúng 1 đáp án đúng (đang có " + correctCount + ")");
    }
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " không được để trống");
    }
    return value.strip();
  }
}

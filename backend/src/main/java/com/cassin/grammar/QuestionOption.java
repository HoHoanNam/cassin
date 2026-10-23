package com.cassin.grammar;

import java.util.ArrayList;
import java.util.List;

import com.cassin.quiz.AttemptAnswer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một đáp án của câu hỏi trắc nghiệm.
 *
 * Là entity riêng (không phải @ElementCollection) vì cần id: bài làm sẽ lưu
 * "người dùng chọn đáp án số 57".
 *
 * Mọi thay đổi phải đi qua Question (aggregate root), vì rule "đúng 4 đáp án, đúng 1 đáp án đúng"
 * là rule của CẢ NHÓM đáp án. Vì vậy constructor và change(...) đều package-private
 * (không ghi public/private): chỉ class cùng package grammar mới gọi được.
 */
@Entity
@Table(name = "question_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionOption {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Owning side (Phía sở hữu): bảng question_options chứa khóa ngoại question_id trỏ sang questions.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  // [SỬA] Bỏ cascade = CascadeType.ALL. Đây là BUG NGUY HIỂM:
  // Question → QuestionOption có cascade ALL, QuestionOption → AttemptAnswer cũng cascade ALL,
  // nên xóa một câu hỏi qua JPA sẽ dây chuyền xóa luôn bài làm của người dùng.
  // Hibernate tự chạy DELETE cho attempt_answers TRƯỚC, nên "chặn xóa" ở DB (V7) không kịp chặn.
  // Bỏ cascade thì Hibernate không động vào bài làm, DB sẽ từ chối xóa câu hỏi đã có người làm.
  // Lưu ý: list này chứa câu trả lời của MỌI người dùng, đừng trả ra API.
  @OneToMany(mappedBy = "selectedOption")
  private List<AttemptAnswer> answers = new ArrayList<>();

  @Column(nullable = false, length = 255)
  private String content;

  // [SỬA] Đổi tên field isCorrect → correct. Với field boolean, Lombok tự sinh getter isXxx():
  // field "correct" → isCorrect(). Field "isCorrect" cũng ra isCorrect() nhưng dễ gây rối
  // với các thư viện khác (Jackson có thể xuất JSON thành "correct" thay vì "isCorrect").
  @Column(name = "is_correct", nullable = false)
  private boolean correct;

  // Thứ tự hiển thị 0..3. Không bao giờ đổi sau khi tạo.
  @Column(nullable = false)
  private int position;

  // [THÊM] Chỉ Question gọi (package-private). Dữ liệu đã được Question kiểm tra trước.
  QuestionOption(Question question, String content, boolean correct, int position) {
    this.question = question;
    this.content = content;
    this.correct = correct;
    this.position = position;
  }

  // [THÊM] Sửa tại chỗ, GIỮ NGUYÊN id: bài làm cũ trỏ tới id này vẫn còn hợp lệ.
  void change(String content, boolean correct) {
    this.content = content;
    this.correct = correct;
  }
}

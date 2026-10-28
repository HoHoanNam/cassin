package com.cassin.quiz;

import com.cassin.grammar.Question;
import com.cassin.grammar.QuestionOption;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MỘT DÒNG trên tờ bài làm (QuizAttempt): câu số mấy, là câu hỏi nào, đã chọn đáp án nào, đúng hay sai.
 *
 * ── Chỉ TRỎ TỚI câu hỏi, không chép nội dung ────────────────────────────────────────
 *   Bảng attempt_answers chỉ lưu:
 *     position       : câu số mấy trên tờ này (0..9)
 *     question       : TRỎ TỚI câu hỏi trong ngân hàng đề (chỉ lưu question_id)
 *     selectedOption : TRỎ TỚI đáp án đã chọn (chỉ lưu selected_option_id); null = bỏ trống
 *     correct        : đúng hay sai (bỏ trống = sai)
 *   Đề bài, 4 đáp án, lời giải thích chỉ nằm trong Question / QuestionOption.
 *   Cần hiển thị thì đi theo quan hệ: answer.getQuestion().getContent().
 *   Nhờ vậy nội dung câu hỏi không bị chép lặp lại vào hàng nghìn bài làm.
 *
 * ── Một câu hỏi, nhiều bài làm ──────────────────────────────────────────────────────
 *   Ngân hàng đề chỉ có MỘT câu #12, nhưng nó có thể nằm trên nhiều tờ, mỗi tờ một lựa chọn khác:
 *
 *     attempt | position | question | selected_option | correct
 *     1       | 0        | #12      | B               | true
 *     1       | 1        | #7       | null (bỏ trống) | false
 *     9       | 2        | #12      | A               | false
 *
 *   Vì vậy "đáp án đã chọn" không thể nằm trong Question (giống "đang ở hộp mấy"
 *   không thể nằm trong Card) → cần entity riêng này.
 *
 * ── Vòng đời ────────────────────────────────────────────────────────────────────────
 *   Tạo NGAY LÚC BẮT ĐẦU quiz (QuizAttempt.start), chưa chọn gì: selectedOption = null, correct = false.
 *   Lúc nộp bài (QuizAttempt.submit), grade(...) điền đáp án đã chọn và chấm đúng/sai.
 *
 * Mọi thay đổi đi qua QuizAttempt (aggregate root), nên constructor và grade(...) là package-private
 * (không ghi public): chỉ class cùng package quiz gọi được.
 */
@Entity
@Table(name = "attempt_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // optional = false -> Bắt buộc mối quan hệ này phải tồn tại (không được null)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_attempt_id", nullable = false)
    private QuizAttempt attempt;

    // [THÊM] Thiếu quan hệ quan trọng nhất: câu trả lời này là cho CÂU HỎI nào?
    // Không có field này thì không biết chấm câu nào, cũng không hiển thị lại được đề.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    // Null = người dùng bỏ trống câu này (hoặc chưa nộp bài).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    @Column(nullable = false)
    private boolean correct = false;

    // [SỬA] Đổi tên "option" → "position": đây là thứ tự câu trong đề (0..9),
    // không phải đáp án. Tên "option" dễ nhầm với selectedOption.
    @Column(nullable = false)
    private int position;

    // [THÊM] Chỉ QuizAttempt.start(...) gọi.
    AttemptAnswer(QuizAttempt attempt, Question question, int position) {
        this.attempt = attempt;
        this.question = question;
        this.position = position;
    }

    /**
     * [THÊM] Chấm câu này. optionId = null nghĩa là bỏ trống → tính sai.
     *
     * question.findOption(...) ném lỗi nếu đáp án không thuộc câu hỏi này
     * (người dùng gửi id đáp án của câu 3 cho câu 5).
     */
    void grade(Long optionId) {
        if (optionId == null) {
            this.selectedOption = null;
            this.correct = false;
            return;
        }
        QuestionOption option = question.findOption(optionId);
        this.selectedOption = option;
        this.correct = option.isCorrect();
    }
}

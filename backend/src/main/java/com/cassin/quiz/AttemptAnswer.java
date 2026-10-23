package com.cassin.quiz;

import com.cassin.grammar.Question;
import com.cassin.grammar.QuestionOption;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một câu trong một lượt làm quiz.
 *
 * Được tạo NGAY LÚC BẮT ĐẦU quiz (chưa có đáp án chọn) để "chốt" đề:
 * lúc nộp bài chỉ điền vào các dòng có sẵn, người dùng không tự thêm câu hỏi khác vào được.
 *
 * Mọi thay đổi đi qua QuizAttempt (aggregate root), nên constructor và grade(...) là package-private.
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

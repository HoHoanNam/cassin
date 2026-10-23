package com.cassin.quiz;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cassin.grammar.GrammarTopic;
import com.cassin.grammar.Question;
import com.cassin.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một lượt làm quiz ngữ pháp của một người dùng.
 *
 * Vòng đời: start(...) chốt danh sách câu hỏi → submit(...) chấm điểm đúng MỘT lần.
 * QuizAttempt là aggregate root của nhóm QuizAttempt + AttemptAnswer.
 */
@Entity
@Table(name = "quiz_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {
    /** Số câu tối đa của một lượt làm bài. Service lấy ngẫu nhiên tối đa chừng này câu. */
    public static final int MAX_QUESTIONS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // [SỬA] @OneToOne → @ManyToOne, và bỏ unique = true. Đây là BUG NẶNG NHẤT:
    // @OneToOne + unique nghĩa là mỗi người dùng chỉ được làm quiz ĐÚNG MỘT LẦN trong đời.
    // Lượt thứ hai sẽ bị DB từ chối vì trùng user_id.
    // Đúng ra: một người làm NHIỀU lượt → nhiều quiz_attempts trỏ về một user → @ManyToOne.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // [SỬA] @OneToOne → @ManyToOne: một chủ đề được làm nhiều lần.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private GrammarTopic topic;

    // Câu trả lời không tồn tại độc lập, luôn tạo cùng lượt làm bài → cascade là đúng
    // (giống Question → QuestionOption).
    // [THÊM] orphanRemoval: câu trả lời bị bỏ khỏi list thì xóa khỏi DB.
    // [THÊM] @OrderBy: luôn trả câu theo đúng thứ tự trong đề.
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<AttemptAnswer> answers = new ArrayList<>();

    // Dùng Integer thay vì int vì có thể null (chưa nộp bài thì chưa có điểm)
    // [SỬA] Thêm @Column cho rõ tên cột.
    @Column(name = "score")
    private Integer score;

    @Column(nullable = false)
    private int total;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    // Null = chưa nộp.
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /**
     * [THÊM] Bắt đầu một lượt làm bài với danh sách câu hỏi đã được chọn (ngẫu nhiên, ở repository).
     * Tạo sẵn một AttemptAnswer cho mỗi câu để "chốt" đề.
     */
    public static QuizAttempt start(User user, GrammarTopic topic, List<Question> questions) {
        Objects.requireNonNull(user, "User không được null");
        Objects.requireNonNull(topic, "Chủ đề không được null");
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Chủ đề này chưa có câu hỏi nào");
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Một lượt làm bài tối đa " + MAX_QUESTIONS + " câu");
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.user = user;
        attempt.topic = topic;
        attempt.startedAt = Instant.now();

        // Lại là mẹo HashSet.add trả về false khi trùng, lần này với id câu hỏi.
        Set<Long> seenQuestionIds = new HashSet<>();
        for (int position = 0; position < questions.size(); position++) {
            Question question = Objects.requireNonNull(questions.get(position), "Câu hỏi không được null");

            // Mọi câu phải thuộc đúng chủ đề đã chọn.
            // getTopic().getId() trên quan hệ LAZY không làm Hibernate truy vấn thêm (id có sẵn trong topic_id).
            if (!Objects.equals(question.getTopic().getId(), topic.getId())) {
                throw new IllegalArgumentException("Câu hỏi " + question.getId() + " không thuộc chủ đề này");
            }
            if (!seenQuestionIds.add(question.getId())) {
                throw new IllegalArgumentException("Câu hỏi " + question.getId() + " bị lặp trong đề");
            }

            attempt.answers.add(new AttemptAnswer(attempt, question, position));
        }
        attempt.total = questions.size();
        return attempt;
    }

    /**
     * [THÊM] Nộp bài và chấm điểm.
     *
     * @param selections key = id câu hỏi, value = id đáp án người dùng chọn.
     *                   Câu không có trong map (hoặc value null) = bỏ trống → tính sai.
     */
    public void submit(Map<Long, Long> selections) {
        // Rule 3: mỗi lượt chỉ nộp một lần.
        if (isSubmitted()) {
            throw new IllegalStateException("Bài này đã được nộp");
        }
        Map<Long, Long> chosen = (selections == null) ? Map.of() : selections;

        // Mọi câu người dùng gửi lên phải nằm trong đề của lượt này.
        Set<Long> questionIdsInAttempt = new HashSet<>();
        for (AttemptAnswer answer : answers) {
            questionIdsInAttempt.add(answer.getQuestion().getId());
        }
        for (Long questionId : chosen.keySet()) {
            if (!questionIdsInAttempt.contains(questionId)) {
                throw new IllegalArgumentException("Câu hỏi " + questionId + " không có trong đề này");
            }
        }

        // Chấm từng câu. Logic "đáp án có đúng không" nằm sẵn trong Question, ở đây chỉ gọi lại.
        // Lưu ý hiệu năng: mỗi câu sẽ tải Question + options (LAZY) → khoảng 20 câu truy vấn cho 10 câu hỏi.
        // Chấp nhận được lúc này; tối ưu bằng fetch join ở repository khi cần.
        int correctCount = 0;
        for (AttemptAnswer answer : answers) {
            answer.grade(chosen.get(answer.getQuestion().getId()));
            if (answer.isCorrect()) {
                correctCount++;
            }
        }

        this.score = correctCount;
        this.submittedAt = Instant.now();
    }

    /** [THÊM] */
    public boolean isSubmitted() {
        return submittedAt != null;
    }

    /** [THÊM] Danh sách chỉ đọc: bên ngoài không tự add/xóa câu được (câu tự kiểm tra số 5). */
    public List<AttemptAnswer> getAnswers() {
        return Collections.unmodifiableList(answers);
    }
}

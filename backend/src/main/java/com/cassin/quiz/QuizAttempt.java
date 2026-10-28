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
 * MỘT lần làm quiz của MỘT người dùng trên MỘT chủ đề ngữ pháp, kèm kết quả của lần đó.
 * Ngoài đời giống: một TỜ BÀI LÀM.
 *
 * ── Đề thi và bài làm ───────────────────────────────────────────────────────────────
 *   Question + QuestionOption : NGÂN HÀNG ĐỀ, chung cho mọi người, không đổi khi có người làm bài.
 *   QuizAttempt               : một tờ bài làm (riêng một người, một lần).
 *   AttemptAnswer             : một dòng trên tờ bài làm (xem AttemptAnswer).
 *   Giống cặp Card (chung) → CardProgress (riêng từng người) bên phần từ vựng.
 *
 * ── Mỗi lần làm bài là MỘT dòng mới ─────────────────────────────────────────────────
 *   Một người làm cùng một chủ đề 3 lần = 3 QuizAttempt, 3 id khác nhau,
 *   cùng user_id (cùng một người) và cùng topic_id (cùng chủ đề):
 *
 *     id | user_id  | topic_id             | score | total | started_at
 *     1  | 5 (An)   | 2 (Present Perfect)  | 6     | 10    | 25/10
 *     4  | 5 (An)   | 2 (Present Perfect)  | 8     | 10    | 26/10
 *     9  | 5 (An)   | 2 (Present Perfect)  | 10    | 10    | 27/10
 *
 *   id không liền nhau là bình thường: người khác làm bài xen giữa, BIGSERIAL cấp id chung cho cả bảng.
 *   "Số lần làm bài" = đếm số dòng; "lịch sử của An" = mọi dòng có user_id = 5.
 *
 * ── Thông tin của một lần làm bài ───────────────────────────────────────────────────
 *   user        : của ai          (nhiều lượt trỏ về một user → @ManyToOne)
 *   topic       : chủ đề gì       (nhiều lượt trỏ về một chủ đề → @ManyToOne)
 *   answers     : các dòng trên tờ, tối đa MAX_QUESTIONS (10) → List<AttemptAnswer>
 *   score       : số câu đúng; null = chưa nộp
 *   total       : tổng số câu trong đề
 *   startedAt   : lúc bắt đầu
 *   submittedAt : lúc nộp; null = chưa nộp (đang làm dở hoặc bỏ dở)
 *
 * ── Vòng đời ────────────────────────────────────────────────────────────────────────
 *   1. start(...)  PHÁT TỜ: tạo sẵn một AttemptAnswer cho mỗi câu, chưa chọn gì, chưa có điểm.
 *                  Việc này "chốt đề": server đã ghi lại tờ này gồm những câu nào,
 *                  lúc nộp người dùng không tự thay / thêm câu khác vào được.
 *   2. submit(...) THU BÀI: điền đáp án đã chọn vào từng dòng, chấm đúng/sai, tính điểm.
 *                  Mỗi tờ chỉ nộp được MỘT lần.
 *
 * QuizAttempt là aggregate root của nhóm QuizAttempt + AttemptAnswer: tờ bài làm quản lý các dòng
 * của nó, bên ngoài không tự thêm / xóa / chấm dòng nào, mọi thay đổi đi qua start(...) và submit(...).
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
        // [SỬA] Cập nhật comment hiệu năng: Question và options đều LAZY, nhưng nhờ
        // default_batch_fetch_size: 50 (application.yml) Hibernate gom lại: 1 câu SQL tải cả 10 Question,
        // 1 câu tải đáp án của cả 10 câu → vài câu SQL, không phải ~20 câu như ghi chú cũ.
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

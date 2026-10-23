import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';

import { QUIZ_MOCK } from './quiz.mock';
import { QuizAnswerResult, QuizResult, QuizSession } from './quiz.models';

@Injectable({ providedIn: 'root' })
export class QuizService {
  private nextAttemptId = 100;

  /**
   * Bắt đầu một lượt làm bài. API thật: POST /api/quizzes { "topicId": 1 }.
   * Trả null nếu không có chủ đề này.
   */
  start(topicId: number): Observable<QuizSession | null> {
    const topic = QUIZ_MOCK[topicId];
    if (!topic) {
      return of(null).pipe(delay(250));
    }
    return of({
      attemptId: this.nextAttemptId++,
      topicTitle: topic.title,
      level: topic.level,
      // Bỏ đáp án đúng và lời giải trước khi đưa cho giao diện, giống server sẽ làm.
      questions: topic.questions.map(({ id, content, options }) => ({ id, content, options })),
    }).pipe(delay(250));
  }

  /**
   * Nộp bài. API thật: POST /api/quizzes/{attemptId}/submit, body là các cặp câu hỏi / đáp án.
   * Mock cần topicId để tra đáp án; API thật chỉ cần attemptId (server đã "chốt" đề lúc bắt đầu).
   *
   * @param selections key = id câu hỏi, value = id đáp án đã chọn (giống Map ở QuizAttempt.submit).
   */
  submit(topicId: number, selections: Record<number, number>): Observable<QuizResult> {
    const questions = QUIZ_MOCK[topicId]?.questions ?? [];
    const answers: QuizAnswerResult[] = questions.map((q) => {
      const selectedOptionId = selections[q.id] ?? null;
      return {
        questionId: q.id,
        selectedOptionId,
        correctOptionId: q.correctOptionId,
        correct: selectedOptionId === q.correctOptionId,
        explanation: q.explanation,
      };
    });
    const score = answers.filter((a) => a.correct).length;
    return of({ score, total: questions.length, answers }).pipe(delay(300));
  }
}

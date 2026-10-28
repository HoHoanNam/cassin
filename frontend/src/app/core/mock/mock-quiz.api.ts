import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { QuizApi } from '../api/quiz.api';
import { ApiError, PageResponse } from '../models/common.models';
import { QuizHistoryItem, QuizResult, QuizStart, SubmitQuizRequest } from '../models/quiz.models';
import { DbAttempt, DbQuestion, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { conflict, notFound, shuffle, simulate } from './mock-utils';

const MAX_QUESTIONS = 10;

/** Giả lập QuizService + vòng đời QuizAttempt (start chốt đề → submit chấm một lần). */
@Injectable()
export class MockQuizApi extends QuizApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  start(topicId: number): Observable<QuizStart> {
    return simulate(() => {
      const userId = this.session.userId();
      const topic = this.db.topics.find((t) => t.id === topicId);
      if (!topic) {
        throw notFound('TOPIC_NOT_FOUND', 'Không tìm thấy chủ đề');
      }
      const questions = shuffle(this.db.questions.filter((q) => q.topicId === topicId)).slice(0, MAX_QUESTIONS);
      if (questions.length === 0) {
        throw conflict('TOPIC_HAS_NO_QUESTIONS', 'Chủ đề này chưa có câu hỏi');
      }
      const attempt: DbAttempt = {
        id: this.db.nextId(),
        userId,
        topicId,
        questionIds: questions.map((q) => q.id),
        selections: {},
        score: null,
        total: questions.length,
        startedAt: new Date().toISOString(),
        submittedAt: null,
      };
      this.db.attempts.push(attempt);
      this.db.save();
      return {
        attemptId: attempt.id,
        topicId,
        topicTitle: topic.title,
        total: attempt.total,
        // KHÔNG trả cờ correct: chỉ id + nội dung đáp án.
        questions: questions.map((q, position) => ({
          questionId: q.id,
          position,
          content: q.content,
          options: q.options.map((o) => ({ id: o.id, content: o.content })),
        })),
      };
    });
  }

  submit(attemptId: number, request: SubmitQuizRequest): Observable<QuizResult> {
    return simulate(() => {
      const attempt = this.findOwn(attemptId);
      if (attempt.submittedAt) {
        throw conflict('QUIZ_ALREADY_SUBMITTED', 'Bài này đã được nộp');
      }
      const selections: Record<number, number | null> = {};
      for (const answer of request.answers ?? []) {
        if (answer.questionId in selections) {
          throw new ApiError(400, 'DUPLICATE_ANSWER', `Câu hỏi ${answer.questionId} được trả lời 2 lần`);
        }
        if (!attempt.questionIds.includes(answer.questionId)) {
          throw new ApiError(400, 'INVALID_ANSWER', `Câu hỏi ${answer.questionId} không có trong đề này`);
        }
        const question = this.question(answer.questionId);
        if (answer.optionId !== null && !question.options.some((o) => o.id === answer.optionId)) {
          throw new ApiError(400, 'INVALID_ANSWER', 'Đáp án không thuộc câu hỏi này');
        }
        selections[answer.questionId] = answer.optionId;
      }
      attempt.selections = selections;
      attempt.score = attempt.questionIds.filter((qid) => {
        const chosen = selections[qid];
        return chosen != null && this.question(qid).options.find((o) => o.id === chosen)?.correct === true;
      }).length;
      attempt.submittedAt = new Date().toISOString();
      this.db.save();
      return this.toResult(attempt);
    });
  }

  result(attemptId: number): Observable<QuizResult> {
    return simulate(() => {
      const attempt = this.findOwn(attemptId);
      if (!attempt.submittedAt) {
        throw conflict('QUIZ_NOT_SUBMITTED', 'Bài này chưa được nộp');
      }
      return this.toResult(attempt);
    });
  }

  history(page: number, size: number): Observable<PageResponse<QuizHistoryItem>> {
    return simulate(() => {
      const userId = this.session.userId();
      const safeSize = Math.min(Math.max(size, 1), 50);
      const safePage = Math.max(page, 0);
      const mine = this.db.attempts
        .filter((a) => a.userId === userId)
        .sort((a, b) => b.startedAt.localeCompare(a.startedAt));
      return {
        content: mine.slice(safePage * safeSize, (safePage + 1) * safeSize).map((a) => ({
          attemptId: a.id,
          topicId: a.topicId,
          topicTitle: this.db.topics.find((t) => t.id === a.topicId)?.title ?? '',
          score: a.score,
          total: a.total,
          startedAt: a.startedAt,
          submittedAt: a.submittedAt,
        })),
        page: safePage,
        size: safeSize,
        totalElements: mine.length,
        totalPages: Math.ceil(mine.length / safeSize),
      };
    });
  }

  private findOwn(attemptId: number): DbAttempt {
    const attempt = this.db.attempts.find((a) => a.id === attemptId);
    if (!attempt || attempt.userId !== this.session.userId()) {
      throw notFound('ATTEMPT_NOT_FOUND', 'Không tìm thấy lượt làm bài');
    }
    return attempt;
  }

  private question(id: number): DbQuestion {
    return this.db.questions.find((q) => q.id === id)!;
  }

  private toResult(attempt: DbAttempt): QuizResult {
    return {
      attemptId: attempt.id,
      topicId: attempt.topicId,
      topicTitle: this.db.topics.find((t) => t.id === attempt.topicId)?.title ?? '',
      score: attempt.score ?? 0,
      total: attempt.total,
      submittedAt: attempt.submittedAt!,
      answers: attempt.questionIds.map((qid, position) => {
        const q = this.question(qid);
        const selected = attempt.selections[qid] ?? null;
        const correctOption = q.options.find((o) => o.correct)!;
        return {
          questionId: q.id,
          position,
          content: q.content,
          options: q.options.map((o) => ({ id: o.id, content: o.content })),
          selectedOptionId: selected,
          correctOptionId: correctOption.id,
          correct: selected === correctOption.id,
          explanation: q.explanation,
        };
      }),
    };
  }
}

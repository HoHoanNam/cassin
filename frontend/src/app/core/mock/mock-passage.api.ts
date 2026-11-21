import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { PassageApi, StartedAttempt } from '../api/passage.api';
import { ApiError, PageResponse } from '../models/common.models';
import { CefrLevel } from '../models/grammar.models';
import {
  AdminPassage,
  AdminPassageSummary,
  AttemptResponse,
  AttemptResult,
  BLANK_MARKER,
  HintResponse,
  PASSAGE_LIMITS,
  PassageRequest,
  PassageStatus,
  PassageSummary,
  PassageView,
  QuestionRequest,
  SubmitRequest,
} from '../models/passage.models';
import { blankInstruction, checkBlankAnswer, countWords, normalizeAnswer } from '../util/passage-text';
import { DbPassage, DbReadingAttempt, DbReadingQuestion, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { conflict, isBlank, notFound, simulate, slicePage, tooLong, validate } from './mock-utils';

/**
 * [THÊM] Giả lập PassageService + AdminPassageService của backend, cùng luật và cùng mã lỗi:
 *
 *   Học viên                                              Admin
 *   1. chỉ thấy bài PUBLISHED        → 404 PASSAGE_NOT_FOUND   5. dữ liệu sai luật       → 400 INVALID_PASSAGE ("Câu 3: ...")
 *   2. lượt của người khác           → 404 ATTEMPT_NOT_FOUND   6. bài đã có người làm    → 409 PASSAGE_LOCKED
 *   3. đã nộp / hết gợi ý            → 409                     7. sai trạng thái         → 409 PASSAGE_STATUS
 *   4. câu lạ / gửi trùng            → 400 INVALID_ANSWER
 */
@Injectable()
export class MockPassageApi extends PassageApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  // ───────────── Học viên ─────────────

  list(level: CefrLevel | null, page: number, size: number): Observable<PageResponse<PassageSummary>> {
    return simulate(() => {
      const userId = this.session.userId();
      const visible = this.db.passages
        .filter((p) => p.status === 'PUBLISHED' && (!level || p.level === level))
        .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? '') || b.id - a.id);
      return slicePage(
        visible.map((p) => ({
          id: p.id,
          title: p.title,
          level: p.level,
          wordCount: countWords(p.content),
          readingMinutes: readingMinutes(p.content),
          questionCount: p.questions.length,
          bestScore: this.bestScore(userId, p.id),
          publishedAt: p.publishedAt!,
        })),
        page,
        size,
      );
    });
  }

  start(passageId: number): Observable<StartedAttempt> {
    return simulate(() => {
      const userId = this.session.userId();
      const passage = this.db.passages.find((p) => p.id === passageId && p.status === 'PUBLISHED');
      if (!passage) {
        throw notFound('PASSAGE_NOT_FOUND', 'Không tìm thấy bài đọc');
      }
      const open = this.db.readingAttempts
        .filter((a) => a.userId === userId && a.passageId === passageId && !a.submittedAt)
        .sort((a, b) => b.startedAt.localeCompare(a.startedAt))[0];
      if (open) {
        return { attempt: this.toAttempt(open, passage), created: false };
      }
      const attempt: DbReadingAttempt = {
        id: this.db.nextId(),
        userId,
        passageId,
        score: null,
        total: passage.questions.length,
        startedAt: new Date().toISOString(),
        submittedAt: null,
        answers: passage.questions.map((q) => ({
          questionId: q.id,
          position: q.position,
          optionId: null,
          tfngAnswer: null,
          text: null,
          correct: false,
          rejectedReason: null,
          hintsUsed: 0,
        })),
      };
      this.db.readingAttempts.push(attempt);
      this.db.save();
      return { attempt: this.toAttempt(attempt, passage), created: true };
    });
  }

  revealHint(attemptId: number, questionId: number): Observable<HintResponse> {
    return simulate(() => {
      const attempt = this.ownedAttempt(attemptId);
      if (attempt.submittedAt) {
        throw conflict('ATTEMPT_SUBMITTED', 'Lượt làm bài đã nộp, hãy xem kết quả');
      }
      const answer = attempt.answers.find((a) => a.questionId === questionId);
      if (!answer) {
        throw new ApiError(400, 'INVALID_ANSWER', 'Câu hỏi không thuộc lượt làm bài này');
      }
      const question = this.question(attempt, questionId);
      const hint = question.hints[answer.hintsUsed];
      if (!hint) {
        throw conflict('NO_MORE_HINTS', 'Câu này đã hết gợi ý');
      }
      answer.hintsUsed += 1;
      this.db.save();
      return {
        questionId,
        hint: { questionPhrase: hint.questionPhrase, passagePhrase: hint.passagePhrase },
        hintsRemaining: question.hints.length - answer.hintsUsed,
      };
    });
  }

  submit(attemptId: number, request: SubmitRequest): Observable<AttemptResult> {
    return simulate(() => {
      const attempt = this.ownedAttempt(attemptId);
      if (attempt.submittedAt) {
        throw conflict('ATTEMPT_SUBMITTED', 'Lượt làm bài đã nộp, hãy xem kết quả');
      }
      const inputs = new Map<number, (typeof request.answers)[number]>();
      for (const answer of request.answers) {
        if (inputs.has(answer.questionId)) {
          throw new ApiError(400, 'INVALID_ANSWER', `Câu hỏi ${answer.questionId} được gửi hai lần`);
        }
        if (!attempt.answers.some((a) => a.questionId === answer.questionId)) {
          throw new ApiError(400, 'INVALID_ANSWER', 'Câu hỏi không thuộc lượt làm bài này');
        }
        inputs.set(answer.questionId, answer);
      }
      for (const answer of attempt.answers) {
        const question = this.question(attempt, answer.questionId);
        const input = inputs.get(answer.questionId);
        answer.optionId = question.type === 'MCQ' ? (input?.optionId ?? null) : null;
        answer.tfngAnswer = question.type === 'TFNG' ? (input?.tfngAnswer ?? null) : null;
        answer.text = question.type === 'BLANK' && input?.text?.trim() ? input.text.trim() : null;
        answer.rejectedReason = null;
        answer.correct = false;
        if (question.type === 'MCQ' && answer.optionId !== null) {
          const option = question.options.find((o) => o.id === answer.optionId);
          if (!option) {
            throw new ApiError(400, 'INVALID_ANSWER', `Câu ${question.position + 1}: lựa chọn không thuộc câu hỏi này`);
          }
          answer.correct = option.correct;
        } else if (question.type === 'TFNG') {
          answer.correct = answer.tfngAnswer !== null && answer.tfngAnswer === question.answer;
        } else if (question.type === 'BLANK' && answer.text) {
          answer.rejectedReason = checkBlankAnswer(question.answerKind!, question.maxWords, answer.text);
          answer.correct =
            !answer.rejectedReason &&
            question.acceptedAnswers.some((a) => normalizeAnswer(a) === normalizeAnswer(answer.text));
        }
      }
      attempt.score = attempt.answers.filter((a) => a.correct).length;
      attempt.submittedAt = new Date().toISOString();
      this.db.save();
      return this.toResult(attempt);
    });
  }

  result(attemptId: number): Observable<AttemptResult> {
    return simulate(() => {
      const attempt = this.ownedAttempt(attemptId);
      if (!attempt.submittedAt) {
        throw conflict('ATTEMPT_NOT_SUBMITTED', 'Bạn chưa nộp bài');
      }
      return this.toResult(attempt);
    });
  }

  // ───────────── Quản trị ─────────────

  adminList(status: PassageStatus | null, page: number, size: number): Observable<PageResponse<AdminPassageSummary>> {
    return simulate(() => {
      const rows = this.db.passages
        .filter((p) => !status || p.status === status)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id);
      return slicePage(
        rows.map((p) => ({
          id: p.id,
          title: p.title,
          level: p.level,
          status: p.status,
          wordCount: countWords(p.content),
          questionCount: p.questions.length,
          updatedAt: p.updatedAt,
          publishedAt: p.publishedAt,
        })),
        page,
        size,
      );
    });
  }

  adminGet(passageId: number): Observable<AdminPassage> {
    return simulate(() => this.toAdmin(this.findPassage(passageId)));
  }

  adminCreate(request: PassageRequest): Observable<AdminPassage> {
    return simulate(() => {
      const now = new Date().toISOString();
      const passage: DbPassage = {
        id: this.db.nextId(),
        title: '',
        content: '',
        level: request.level,
        status: 'DRAFT',
        createdById: this.session.userId(),
        createdAt: now,
        updatedAt: now,
        publishedAt: null,
        questions: [],
      };
      this.apply(passage, request);
      this.db.passages.push(passage);
      this.db.save();
      return this.toAdmin(passage);
    });
  }

  adminUpdate(passageId: number, request: PassageRequest): Observable<AdminPassage> {
    return simulate(() => {
      const passage = this.findPassage(passageId);
      this.requireNotLocked(passageId);
      if (passage.status === 'ARCHIVED') {
        throw conflict('PASSAGE_STATUS', 'Bài đã lưu trữ, không sửa được');
      }
      const copy: DbPassage = structuredClone(passage);
      this.apply(copy, request); // lỗi ở đây thì bản thật không bị đổi (giống rollback)
      if (copy.status === 'PUBLISHED' && copy.questions.length === 0) {
        throw invalid('Bài đọc phải có ít nhất 1 câu hỏi');
      }
      Object.assign(passage, copy);
      this.db.save();
      return this.toAdmin(passage);
    });
  }

  adminPublish(passageId: number): Observable<AdminPassage> {
    return simulate(() => {
      const passage = this.findPassage(passageId);
      if (passage.status !== 'DRAFT') {
        throw conflict('PASSAGE_STATUS', 'Chỉ bài nháp mới publish được');
      }
      if (passage.questions.length === 0) {
        throw invalid('Bài đọc phải có ít nhất 1 câu hỏi');
      }
      passage.status = 'PUBLISHED';
      passage.publishedAt = passage.updatedAt = new Date().toISOString();
      this.db.save();
      return this.toAdmin(passage);
    });
  }

  adminArchive(passageId: number): Observable<AdminPassage> {
    return simulate(() => {
      const passage = this.findPassage(passageId);
      if (passage.status !== 'PUBLISHED') {
        throw conflict('PASSAGE_STATUS', 'Chỉ bài đang publish mới lưu trữ được');
      }
      passage.status = 'ARCHIVED';
      passage.updatedAt = new Date().toISOString();
      this.db.save();
      return this.toAdmin(passage);
    });
  }

  adminDelete(passageId: number): Observable<void> {
    return simulate(() => {
      this.findPassage(passageId);
      this.requireNotLocked(passageId);
      this.db.removeWhere('passages', (p) => p.id === passageId);
      this.db.save();
    });
  }

  // ───────────── helpers ─────────────

  /** Giống Passage.update + addQuestion ở backend: kiểm tra từng câu, lỗi kèm "Câu N: ". */
  private apply(passage: DbPassage, request: PassageRequest): void {
    validate([
      ['title', isBlank(request.title), 'Vui lòng nhập tiêu đề'],
      ['title', tooLong(request.title, PASSAGE_LIMITS.title), 'Tiêu đề tối đa 150 ký tự'],
      ['content', isBlank(request.content), 'Vui lòng nhập nội dung bài đọc'],
      ['content', tooLong(request.content, PASSAGE_LIMITS.content), 'Bài đọc tối đa 5000 ký tự'],
      ['level', !request.level, 'Vui lòng chọn trình độ'],
      ['questions', request.questions.length > PASSAGE_LIMITS.questions, 'Mỗi bài tối đa 20 câu hỏi'],
    ]);
    const content = request.content.trim();
    const now = new Date().toISOString();
    passage.title = request.title.trim();
    passage.content = content;
    passage.level = request.level;
    passage.updatedAt = now;
    passage.questions = request.questions.map((q, position) => this.toDbQuestion(q, position, content));
  }

  private toDbQuestion(q: QuestionRequest, position: number, content: string): DbReadingQuestion {
    const label = `Câu ${position + 1}: `;
    if (isBlank(q.prompt) || isBlank(q.evidence) || isBlank(q.explanation)) {
      throw invalid(label + 'cần đủ đề, câu bằng chứng và lời giải thích');
    }
    if (!content.includes(q.evidence.trim())) {
      throw invalid(label + 'câu bằng chứng phải trích nguyên văn từ bài đọc');
    }
    if (q.hints.length > PASSAGE_LIMITS.hints) {
      throw invalid(label + 'tối đa 3 gợi ý');
    }
    for (const hint of q.hints) {
      if (!q.prompt.toLowerCase().includes(hint.questionPhrase.trim().toLowerCase())) {
        throw invalid(`${label}không tìm thấy "${hint.questionPhrase}" trong câu hỏi`);
      }
      if (!content.toLowerCase().includes(hint.passagePhrase.trim().toLowerCase())) {
        throw invalid(`${label}không tìm thấy "${hint.passagePhrase}" trong bài đọc`);
      }
    }
    const row: DbReadingQuestion = {
      id: this.db.nextId(),
      position,
      type: q.type,
      prompt: q.prompt.trim(),
      evidence: q.evidence.trim(),
      explanation: q.explanation.trim(),
      hints: q.hints.map((h) => ({ id: this.db.nextId(), questionPhrase: h.questionPhrase.trim(), passagePhrase: h.passagePhrase.trim() })),
      options: [],
      answer: null,
      answerKind: null,
      maxWords: null,
      acceptedAnswers: [],
    };
    if (q.type === 'MCQ') {
      if (q.options.length !== 4 || q.options.filter((o) => o.correct).length !== 1) {
        throw invalid(label + 'phải có đúng 4 lựa chọn và đúng 1 lựa chọn đúng');
      }
      row.options = q.options.map((o) => ({ id: this.db.nextId(), content: o.content.trim(), correct: o.correct }));
    } else if (q.type === 'TFNG') {
      row.answer = q.answer;
    } else {
      if (q.prompt.split(BLANK_MARKER).length - 1 !== 1) {
        throw invalid(`${label}đề phải có đúng một chỗ trống "${BLANK_MARKER}"`);
      }
      const maxWords = q.answerKind === 'NUMBER' ? null : q.maxWords;
      for (const answer of q.acceptedAnswers) {
        if (checkBlankAnswer(q.answerKind, maxWords, answer)) {
          throw invalid(`${label}đáp án "${answer.trim()}" không đúng yêu cầu ${blankInstruction(q.answerKind, maxWords)}`);
        }
      }
      row.answerKind = q.answerKind;
      row.maxWords = maxWords;
      row.acceptedAnswers = q.acceptedAnswers.map((a) => a.trim());
    }
    return row;
  }

  private requireNotLocked(passageId: number): void {
    if (this.db.readingAttempts.some((a) => a.passageId === passageId)) {
      throw conflict('PASSAGE_LOCKED', 'Bài đọc đã có học viên làm, không sửa hay xóa được nữa. Hãy lưu trữ bài và tạo bài mới.');
    }
  }

  private findPassage(passageId: number): DbPassage {
    const passage = this.db.passages.find((p) => p.id === passageId);
    if (!passage) {
      throw notFound('PASSAGE_NOT_FOUND', 'Không tìm thấy bài đọc');
    }
    return passage;
  }

  private ownedAttempt(attemptId: number): DbReadingAttempt {
    const userId = this.session.userId();
    const attempt = this.db.readingAttempts.find((a) => a.id === attemptId && a.userId === userId);
    if (!attempt) {
      throw notFound('ATTEMPT_NOT_FOUND', 'Không tìm thấy lượt làm bài');
    }
    return attempt;
  }

  private question(attempt: DbReadingAttempt, questionId: number): DbReadingQuestion {
    return this.db.passages.find((p) => p.id === attempt.passageId)!.questions.find((q) => q.id === questionId)!;
  }

  private bestScore(userId: number, passageId: number): number | null {
    const scores = this.db.readingAttempts
      .filter((a) => a.userId === userId && a.passageId === passageId && a.submittedAt)
      .map((a) => a.score ?? 0);
    return scores.length ? Math.max(...scores) : null;
  }

  private toView(p: DbPassage): PassageView {
    return { id: p.id, title: p.title, content: p.content, level: p.level, wordCount: countWords(p.content), readingMinutes: readingMinutes(p.content) };
  }

  private toAttempt(attempt: DbReadingAttempt, passage: DbPassage): AttemptResponse {
    return {
      attemptId: attempt.id,
      passage: this.toView(passage),
      startedAt: attempt.startedAt,
      questions: attempt.answers.map((answer) => {
        const q = passage.questions.find((x) => x.id === answer.questionId)!;
        return {
          id: q.id,
          position: q.position,
          type: q.type,
          prompt: q.prompt,
          ...(q.type === 'BLANK' ? { instruction: blankInstruction(q.answerKind!, q.maxWords) } : {}),
          ...(q.type === 'MCQ' ? { options: q.options.map((o, i) => ({ id: o.id, label: 'ABCD'[i], content: o.content })) } : {}),
          revealedHints: q.hints.slice(0, answer.hintsUsed).map(({ questionPhrase, passagePhrase }) => ({ questionPhrase, passagePhrase })),
          hintsRemaining: q.hints.length - answer.hintsUsed,
        };
      }),
    };
  }

  private toResult(attempt: DbReadingAttempt): AttemptResult {
    const passage = this.db.passages.find((p) => p.id === attempt.passageId)!;
    return {
      attemptId: attempt.id,
      passage: this.toView(passage),
      score: attempt.score ?? 0,
      total: attempt.total,
      correctWithoutHints: attempt.answers.filter((a) => a.correct && a.hintsUsed === 0).length,
      startedAt: attempt.startedAt,
      submittedAt: attempt.submittedAt!,
      questions: attempt.answers.map((answer) => {
        const q = passage.questions.find((x) => x.id === answer.questionId)!;
        return {
          questionId: q.id,
          position: q.position,
          type: q.type,
          prompt: q.prompt,
          correct: answer.correct,
          hintsUsed: answer.hintsUsed,
          hints: q.hints.map(({ questionPhrase, passagePhrase }) => ({ questionPhrase, passagePhrase })),
          evidence: q.evidence,
          explanation: q.explanation,
          ...(q.type === 'MCQ'
            ? {
                options: q.options.map((o, i) => ({ id: o.id, label: 'ABCD'[i], content: o.content, correct: o.correct })),
                ...(answer.optionId !== null ? { selectedOptionId: answer.optionId } : {}),
              }
            : {}),
          ...(q.type === 'TFNG'
            ? { correctTfngAnswer: q.answer!, ...(answer.tfngAnswer ? { yourTfngAnswer: answer.tfngAnswer } : {}) }
            : {}),
          ...(q.type === 'BLANK'
            ? {
                instruction: blankInstruction(q.answerKind!, q.maxWords),
                acceptedAnswers: q.acceptedAnswers,
                ...(answer.text ? { yourText: answer.text } : {}),
                ...(answer.rejectedReason ? { rejectedReason: answer.rejectedReason } : {}),
              }
            : {}),
        };
      }),
    };
  }

  private toAdmin(p: DbPassage): AdminPassage {
    return {
      id: p.id,
      title: p.title,
      content: p.content,
      level: p.level,
      status: p.status,
      wordCount: countWords(p.content),
      locked: this.db.readingAttempts.some((a) => a.passageId === p.id),
      createdAt: p.createdAt,
      updatedAt: p.updatedAt,
      publishedAt: p.publishedAt,
      questions: p.questions.map((q) => ({
        id: q.id,
        position: q.position,
        type: q.type,
        prompt: q.prompt,
        evidence: q.evidence,
        explanation: q.explanation,
        hints: q.hints.map(({ questionPhrase, passagePhrase }) => ({ questionPhrase, passagePhrase })),
        ...(q.type === 'MCQ' ? { options: q.options.map((o, i) => ({ id: o.id, label: 'ABCD'[i], content: o.content, correct: o.correct })) } : {}),
        ...(q.type === 'TFNG' ? { answer: q.answer! } : {}),
        ...(q.type === 'BLANK' ? { answerKind: q.answerKind!, maxWords: q.maxWords, acceptedAnswers: q.acceptedAnswers } : {}),
      })),
    };
  }
}

const invalid = (message: string) => new ApiError(400, 'INVALID_PASSAGE', message);

function readingMinutes(content: string): number {
  return Math.max(1, Math.round(countWords(content) / 150));
}

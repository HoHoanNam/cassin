import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AdminApi } from '../api/admin.api';
import {
  AdminQuestion,
  AdminStats,
  QUESTION_LIMITS,
  QuestionRequest,
  TOPIC_LIMITS,
  TopicRequest,
} from '../models/admin.models';
import { Card, CardRequest, Deck, DeckRequest } from '../models/deck.models';
import { CEFR_LEVELS, TopicDetail } from '../models/grammar.models';
import { todayIso } from '../util/dates';
import { DbCard, DbDeck, DbQuestion, DbTopic, MockDb } from './mock-db';
import { assertCardNotDuplicate, cleanExamples, toCard, validateCard, validateDeck } from './mock-deck.api';
import { MockSession } from './mock-session';
import { businessError, conflict, forbidden, isBlank, notFound, simulate, tooLong, validate } from './mock-utils';

/**
 * Giả lập AdminDeckController + AdminGrammarController + AdminStatsController.
 * Cùng luật, cùng mã lỗi với backend:
 *   - Không phải ADMIN                       → 403 FORBIDDEN (SecurityConfig)
 *   - Bộ CÁ NHÂN của user                    → 404 (admin chỉ quản lý bộ hệ thống)
 *   - Bộ / chủ đề / câu hỏi đã có người dùng → 409 DECK_IN_USE / TOPIC_IN_USE / QUESTION_IN_USE
 *   - Sai luật câu hỏi (4 đáp án, đúng 1)    → 400 INVALID_QUESTION
 */
@Injectable()
export class MockAdminApi extends AdminApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  stats(): Observable<AdminStats> {
    return simulate(() => {
      this.requireAdmin();
      const today = todayIso();
      const systemDeckIds = new Set(this.db.decks.filter((d) => d.deckType === 'SYSTEM_DECK').map((d) => d.id));
      return {
        users: this.db.users.length,
        systemDecks: systemDeckIds.size,
        systemCards: this.db.cards.filter((c) => systemDeckIds.has(c.deckId)).length,
        topics: this.db.topics.length,
        questions: this.db.questions.length,
        quizAttempts: this.db.attempts.length,
        learnersToday: this.db.users.filter((u) => this.db.studyDays(u.id).includes(today)).length,
      };
    });
  }

  // ===================== Bộ từ hệ thống =====================

  createDeck(request: DeckRequest): Observable<Deck> {
    return simulate(() => {
      this.requireAdmin();
      validateDeck(request);
      const now = new Date().toISOString();
      const deck: DbDeck = {
        id: this.db.nextId(),
        deckName: request.deckName.trim(),
        description: request.description?.trim() || null,
        deckType: 'SYSTEM_DECK',
        ownerId: null,
        createdAt: now,
        updatedAt: now,
      };
      this.db.decks.push(deck);
      this.db.save();
      return this.toDeck(deck);
    });
  }

  updateDeck(deckId: number, request: DeckRequest): Observable<Deck> {
    return simulate(() => {
      this.requireAdmin();
      const deck = this.findSystemDeck(deckId);
      validateDeck(request);
      deck.deckName = request.deckName.trim();
      deck.description = request.description?.trim() || null;
      deck.updatedAt = new Date().toISOString();
      this.db.save();
      return this.toDeck(deck);
    });
  }

  deleteDeck(deckId: number): Observable<void> {
    return simulate(() => {
      this.requireAdmin();
      // Tìm bộ TRƯỚC, kiểm tra người học SAU (giống bản sửa ở AdminDeckService).
      const deck = this.findSystemDeck(deckId);
      const cardIds = new Set(this.db.cards.filter((c) => c.deckId === deck.id).map((c) => c.id));
      if (this.db.progresses.some((p) => cardIds.has(p.cardId))) {
        throw conflict('DECK_IN_USE', 'Bộ từ đã có người học, không thể xóa');
      }
      this.db.removeWhere('cards', (c) => c.deckId === deck.id);
      this.db.removeWhere('decks', (d) => d.id === deck.id);
      this.db.save();
    });
  }

  createCard(deckId: number, request: CardRequest): Observable<Card> {
    return simulate(() => {
      this.requireAdmin();
      const deck = this.findSystemDeck(deckId);
      validateCard(request);
      assertCardNotDuplicate(this.db, deck.id, request, null);
      const card: DbCard = {
        id: this.db.nextId(),
        deckId: deck.id,
        word: request.word.trim(),
        partOfSpeech: request.partOfSpeech,
        phonetic: request.phonetic?.trim() || null,
        definition: request.definition.trim(),
        examples: cleanExamples(request.examples),
        createdAt: new Date().toISOString(),
      };
      this.db.cards.push(card);
      deck.updatedAt = card.createdAt;
      this.db.save();
      return toCard(card);
    });
  }

  updateCard(cardId: number, request: CardRequest): Observable<Card> {
    return simulate(() => {
      this.requireAdmin();
      const card = this.findSystemCard(cardId);
      validateCard(request);
      assertCardNotDuplicate(this.db, card.deckId, request, card.id);
      card.word = request.word.trim();
      card.partOfSpeech = request.partOfSpeech;
      card.phonetic = request.phonetic?.trim() || null;
      card.definition = request.definition.trim();
      card.examples = cleanExamples(request.examples);
      this.db.save();
      return toCard(card);
    });
  }

  deleteCard(cardId: number): Observable<void> {
    return simulate(() => {
      this.requireAdmin();
      const card = this.findSystemCard(cardId);
      // Xóa MỘT thẻ: tiến độ của thẻ đó bị xóa theo (ON DELETE CASCADE), không chặn như xóa cả bộ.
      this.db.removeWhere('progresses', (p) => p.cardId === card.id);
      this.db.removeWhere('cards', (c) => c.id === card.id);
      this.db.save();
    });
  }

  // ===================== Ngữ pháp =====================

  createTopic(request: TopicRequest): Observable<TopicDetail> {
    return simulate(() => {
      this.requireAdmin();
      validateTopic(request);
      const topic: DbTopic = {
        id: this.db.nextId(),
        title: request.title.trim(),
        level: request.level,
        explanation: request.explanation.trim(),
      };
      this.db.topics.push(topic);
      this.db.save();
      return this.toTopicDetail(topic);
    });
  }

  updateTopic(topicId: number, request: TopicRequest): Observable<TopicDetail> {
    return simulate(() => {
      this.requireAdmin();
      const topic = this.findTopic(topicId);
      validateTopic(request);
      topic.title = request.title.trim();
      topic.level = request.level;
      topic.explanation = request.explanation.trim();
      this.db.save();
      return this.toTopicDetail(topic);
    });
  }

  deleteTopic(topicId: number): Observable<void> {
    return simulate(() => {
      this.requireAdmin();
      const topic = this.findTopic(topicId);
      if (this.db.attempts.some((a) => a.topicId === topic.id)) {
        throw conflict('TOPIC_IN_USE', 'Chủ đề đã có người làm bài, không thể xóa');
      }
      this.db.removeWhere('questions', (q) => q.topicId === topic.id);
      this.db.removeWhere('topics', (t) => t.id === topic.id);
      this.db.save();
    });
  }

  listQuestions(topicId: number): Observable<AdminQuestion[]> {
    return simulate(() => {
      this.requireAdmin();
      const topic = this.findTopic(topicId);
      return this.db.questions
        .filter((q) => q.topicId === topic.id)
        .sort((a, b) => a.id - b.id)
        .map(toAdminQuestion);
    });
  }

  createQuestion(topicId: number, request: QuestionRequest): Observable<AdminQuestion> {
    return simulate(() => {
      this.requireAdmin();
      const topic = this.findTopic(topicId);
      validateQuestion(request);
      const question: DbQuestion = {
        id: this.db.nextId(),
        topicId: topic.id,
        content: request.content.trim(),
        explanation: request.explanation.trim(),
        options: request.options.map((o) => ({ id: this.db.nextId(), content: o.content.trim(), correct: o.correct })),
      };
      this.db.questions.push(question);
      this.db.save();
      return toAdminQuestion(question);
    });
  }

  updateQuestion(questionId: number, request: QuestionRequest): Observable<AdminQuestion> {
    return simulate(() => {
      this.requireAdmin();
      const question = this.findQuestion(questionId);
      validateQuestion(request);
      question.content = request.content.trim();
      question.explanation = request.explanation.trim();
      // Sửa TẠI CHỖ theo vị trí, giữ nguyên id đáp án (giống Question.update): bài làm cũ vẫn hợp lệ.
      request.options.forEach((o, i) => {
        question.options[i].content = o.content.trim();
        question.options[i].correct = o.correct;
      });
      this.db.save();
      return toAdminQuestion(question);
    });
  }

  deleteQuestion(questionId: number): Observable<void> {
    return simulate(() => {
      this.requireAdmin();
      const question = this.findQuestion(questionId);
      if (this.db.attempts.some((a) => a.questionIds.includes(question.id))) {
        throw conflict('QUESTION_IN_USE', 'Câu hỏi đã có người trả lời, hãy sửa thay vì xóa');
      }
      this.db.removeWhere('questions', (q) => q.id === question.id);
      this.db.save();
    });
  }

  // ===================== Helpers =====================

  /** Giống luật .requestMatchers("/api/admin/**").hasRole("ADMIN") trong SecurityConfig. */
  private requireAdmin(): void {
    const user = this.db.users.find((u) => u.id === this.session.userId());
    if (user?.role !== 'ADMIN') {
      throw forbidden('FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này');
    }
  }

  private findSystemDeck(deckId: number): DbDeck {
    const deck = this.db.decks.find((d) => d.id === deckId && d.deckType === 'SYSTEM_DECK');
    if (!deck) {
      throw notFound('DECK_NOT_FOUND', 'Không tìm thấy bộ từ');
    }
    return deck;
  }

  private findSystemCard(cardId: number): DbCard {
    const card = this.db.cards.find((c) => c.id === cardId);
    const deck = card && this.db.decks.find((d) => d.id === card.deckId);
    if (!card || deck?.deckType !== 'SYSTEM_DECK') {
      throw notFound('CARD_NOT_FOUND', 'Không tìm thấy thẻ');
    }
    return card;
  }

  private findTopic(topicId: number): DbTopic {
    const topic = this.db.topics.find((t) => t.id === topicId);
    if (!topic) {
      throw notFound('TOPIC_NOT_FOUND', 'Không tìm thấy chủ đề');
    }
    return topic;
  }

  private findQuestion(questionId: number): DbQuestion {
    const question = this.db.questions.find((q) => q.id === questionId);
    if (!question) {
      throw notFound('QUESTION_NOT_FOUND', 'Không tìm thấy câu hỏi');
    }
    return question;
  }

  /** Admin không "học" bộ hệ thống → dueCount / newCount = 0, editable = false (giống backend). */
  private toDeck(deck: DbDeck): Deck {
    return {
      id: deck.id,
      deckName: deck.deckName,
      description: deck.description,
      deckType: deck.deckType,
      cardCount: this.db.cards.filter((c) => c.deckId === deck.id).length,
      editable: false,
      dueCount: 0,
      newCount: 0,
      updatedAt: deck.updatedAt,
    };
  }

  private toTopicDetail(topic: DbTopic): TopicDetail {
    return {
      id: topic.id,
      title: topic.title,
      level: topic.level,
      explanation: topic.explanation,
      questionCount: this.db.questions.filter((q) => q.topicId === topic.id).length,
    };
  }
}

function toAdminQuestion(question: DbQuestion): AdminQuestion {
  return {
    questionId: question.id,
    content: question.content,
    explanation: question.explanation,
    options: question.options.map((o, position) => ({ optionId: o.id, content: o.content, correct: o.correct, position })),
  };
}

function validateTopic(request: TopicRequest): void {
  validate([
    ['title', isBlank(request.title), 'Vui lòng nhập tiêu đề'],
    ['title', tooLong(request.title, TOPIC_LIMITS.title), `Tiêu đề tối đa ${TOPIC_LIMITS.title} ký tự`],
    ['level', !CEFR_LEVELS.includes(request.level), 'Vui lòng chọn trình độ'],
    ['explanation', isBlank(request.explanation), 'Vui lòng nhập bài giải thích'],
  ]);
}

/**
 * Hai lớp kiểm tra, giống backend:
 *   1. Từng field (@Valid trên QuestionRequest) → 400 VALIDATION_ERROR kèm fieldErrors
 *   2. Luật của cả nhóm đáp án (Question.validateOptions) → 400 INVALID_QUESTION kèm câu thông báo
 */
function validateQuestion(request: QuestionRequest): void {
  const options = request.options ?? [];
  validate([
    ['content', isBlank(request.content), 'Vui lòng nhập câu hỏi'],
    ['explanation', isBlank(request.explanation), 'Vui lòng nhập lời giải thích'],
    ['options', options.length !== QUESTION_LIMITS.optionCount, 'Câu hỏi phải có đúng 4 đáp án'],
    ['options', options.some((o) => isBlank(o.content)), 'Đáp án không được để trống'],
    ['options', options.some((o) => tooLong(o.content, QUESTION_LIMITS.option)), 'Mỗi đáp án tối đa 255 ký tự'],
  ]);
  const seen = new Set<string>();
  for (const option of options) {
    const key = option.content.trim().toLowerCase();
    if (seen.has(key)) {
      throw businessError('INVALID_QUESTION', `Hai đáp án không được trùng nhau: ${option.content.trim()}`);
    }
    seen.add(key);
  }
  const correctCount = options.filter((o) => o.correct).length;
  if (correctCount !== 1) {
    throw businessError('INVALID_QUESTION', `Câu hỏi phải có đúng 1 đáp án đúng (đang có ${correctCount})`);
  }
}

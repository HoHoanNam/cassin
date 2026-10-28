import { Injectable } from '@angular/core';

import { CefrLevel } from '../models/grammar.models';
import { DeckType, PartOfSpeech } from '../models/deck.models';
import { Role } from '../models/user.models';
import { addDays, todayIso } from '../util/dates';
import { SEED_SYSTEM_DECK, SEED_TOPICS, SEED_USER_DECKS, SeedCard } from './mock-seed';

/*
 * "Database" giả trong trình duyệt cho chế độ mock.
 * Mỗi interface ở đây giống MỘT bảng trong PostgreSQL (V1..V7), không phải DTO trả ra API.
 * Mock API đọc/ghi các bảng này rồi tự dựng DTO, giống cách backend làm.
 */

export interface DbUser {
  id: number;
  email: string;
  password: string; // chỉ có trong mock; backend thật lưu chuỗi băm BCrypt
  displayName: string;
  role: Role;
}

export interface DbDeck {
  id: number;
  deckName: string;
  description: string | null;
  deckType: DeckType;
  ownerId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface DbCard {
  id: number;
  deckId: number;
  word: string;
  partOfSpeech: PartOfSpeech;
  phonetic: string | null;
  definition: string;
  examples: string[];
  createdAt: string;
}

export interface DbProgress {
  userId: number;
  cardId: number;
  box: number;
  dueDate: string;
  lastReviewedOn: string | null;
  reviewCount: number;
  lapseCount: number;
}

export interface DbOption {
  id: number;
  content: string;
  correct: boolean;
}

export interface DbTopic {
  id: number;
  title: string;
  level: CefrLevel;
  explanation: string;
}

export interface DbQuestion {
  id: number;
  topicId: number;
  content: string;
  explanation: string;
  options: DbOption[];
}

export interface DbAttempt {
  id: number;
  userId: number;
  topicId: number;
  questionIds: number[];
  selections: Record<number, number | null>;
  score: number | null;
  total: number;
  startedAt: string;
  submittedAt: string | null;
}

interface DbState {
  version: number;
  seq: number;
  users: DbUser[];
  decks: DbDeck[];
  cards: DbCard[];
  progresses: DbProgress[];
  topics: DbTopic[];
  questions: DbQuestion[];
  attempts: DbAttempt[];
  /** Những ngày user có ôn thẻ (để tính chuỗi ngày học liên tiếp). */
  studyDays: Record<number, string[]>;
}

const STORAGE_KEY = 'cassin.mockdb';
const VERSION = 1;

/**
 * Lưu vào localStorage sau mỗi lần ghi: tải lại trang vẫn còn bộ từ / thẻ / tiến độ vừa tạo.
 * Muốn làm lại từ đầu: gọi reset() (nút "Đặt lại dữ liệu mẫu" trong menu tài khoản).
 */
@Injectable({ providedIn: 'root' })
export class MockDb {
  private state: DbState = this.load() ?? buildSeed();

  get users() {
    return this.state.users;
  }
  get decks() {
    return this.state.decks;
  }
  get cards() {
    return this.state.cards;
  }
  get progresses() {
    return this.state.progresses;
  }
  get topics() {
    return this.state.topics;
  }
  get questions() {
    return this.state.questions;
  }
  get attempts() {
    return this.state.attempts;
  }

  /** Cấp id mới, giống BIGSERIAL (dùng chung một dãy số cho mọi bảng cho đơn giản). */
  nextId(): number {
    this.state.seq += 1;
    return this.state.seq;
  }

  /** Xóa các dòng thỏa điều kiện khỏi một bảng (giống DELETE ... WHERE). */
  removeWhere<K extends 'decks' | 'cards' | 'progresses' | 'attempts'>(
    table: K,
    predicate: (row: DbState[K][number]) => boolean,
  ): void {
    const rows = this.state[table] as DbState[K][number][];
    Object.assign(this.state, { [table]: rows.filter((row) => !predicate(row)) });
  }

  studyDays(userId: number): string[] {
    return this.state.studyDays[userId] ?? [];
  }

  markStudied(userId: number, day: string): void {
    const days = this.studyDays(userId);
    if (!days.includes(day)) {
      this.state.studyDays[userId] = [...days, day];
    }
  }

  save(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.state));
    } catch {
      // Không lưu được: dữ liệu vẫn dùng được tới khi tải lại trang.
    }
  }

  reset(): void {
    this.state = buildSeed();
    this.save();
  }

  private load(): DbState | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as DbState) : null;
      return parsed?.version === VERSION ? parsed : null;
    } catch {
      return null;
    }
  }
}

/** Dựng dữ liệu mẫu. Ngày hạn ôn tính tương đối so với hôm nay để trang Hôm nay luôn có việc. */
function buildSeed(): DbState {
  const today = todayIso();
  const now = new Date().toISOString();
  const state: DbState = {
    version: VERSION,
    seq: 1000,
    users: [],
    decks: [],
    cards: [],
    progresses: [],
    topics: [],
    questions: [],
    attempts: [],
    studyDays: {},
  };
  const id = () => ++state.seq;

  const nam: DbUser = { id: 1, email: 'nam@gmail.com', password: '12345678', displayName: 'Nam', role: 'USER' };
  state.users.push(nam);

  const addDeck = (deckName: string, description: string | null, deckType: DeckType, ownerId: number | null, updatedDaysAgo: number) => {
    const deck: DbDeck = {
      id: id(),
      deckName,
      description,
      deckType,
      ownerId,
      createdAt: now,
      updatedAt: new Date(Date.now() - updatedDaysAgo * 86_400_000).toISOString(),
    };
    state.decks.push(deck);
    return deck;
  };
  const addCards = (deckId: number, cards: SeedCard[]) =>
    cards.map(([word, partOfSpeech, phonetic, definition, examples], index) => {
      const card: DbCard = {
        id: id(),
        deckId,
        word,
        partOfSpeech,
        phonetic,
        definition,
        examples,
        createdAt: new Date(Date.now() - (cards.length - index) * 60_000).toISOString(),
      };
      state.cards.push(card);
      return card;
    });

  // Tiến độ mẫu: [hộp, hạn ôn (số ngày so với hôm nay, âm = quá hạn)]
  const plan: [number, number][] = [
    [1, 0], [2, -1], [3, 0], [1, -2], [4, 0], [2, 1], [5, 9], [3, 2],
    [2, 0], [4, 5], [1, 0], [3, -1], [5, 12], [2, 0], [4, 3], [1, 1],
  ];
  const addProgress = (cards: DbCard[], count: number) =>
    cards.slice(0, count).forEach((card, i) => {
      const [box, dueIn] = plan[i % plan.length];
      state.progresses.push({
        userId: nam.id,
        cardId: card.id,
        box,
        dueDate: addDays(today, dueIn),
        lastReviewedOn: dueIn > 0 ? today : addDays(today, -2),
        reviewCount: box + 1,
        lapseCount: box === 1 ? 1 : 0,
      });
    });

  const system = addDeck(SEED_SYSTEM_DECK.deckName, SEED_SYSTEM_DECK.description, 'SYSTEM_DECK', null, 0);
  addProgress(addCards(system.id, SEED_SYSTEM_DECK.cards), 16);

  SEED_USER_DECKS.forEach((seed, index) => {
    const deck = addDeck(seed.deckName, seed.description, 'USER_DECK', nam.id, index === 0 ? 1 : 10);
    const cards = addCards(deck.id, seed.cards);
    addProgress(cards, index === 0 ? 10 : 0);
  });

  for (const seed of SEED_TOPICS) {
    const topic: DbTopic = { id: id(), title: seed.title, level: seed.level, explanation: seed.explanation };
    state.topics.push(topic);
    for (const [content, explanation, options, correctIndex] of seed.questions) {
      state.questions.push({
        id: id(),
        topicId: topic.id,
        content,
        explanation,
        options: options.map((text, i) => ({ id: id(), content: text, correct: i === correctIndex })),
      });
    }
  }

  // Lịch sử làm bài mẫu: [vị trí chủ đề, số câu làm đúng, cách đây mấy ngày]
  const history: [number, number, number][] = [[1, 7, 1], [0, 6, 7], [2, 6, 10]];
  for (const [topicIndex, correctWanted, daysAgo] of history) {
    const topic = state.topics[topicIndex];
    const questions = state.questions.filter((q) => q.topicId === topic.id);
    const selections: Record<number, number | null> = {};
    questions.forEach((q, i) => {
      const correct = q.options.find((o) => o.correct)!;
      const wrong = q.options.find((o) => !o.correct)!;
      selections[q.id] = i < correctWanted ? correct.id : wrong.id;
    });
    const at = new Date(Date.now() - daysAgo * 86_400_000);
    state.attempts.push({
      id: id(),
      userId: nam.id,
      topicId: topic.id,
      questionIds: questions.map((q) => q.id),
      selections,
      score: Math.min(correctWanted, questions.length),
      total: questions.length,
      startedAt: new Date(at.getTime() - 6 * 60_000).toISOString(),
      submittedAt: at.toISOString(),
    });
  }

  state.studyDays[nam.id] = [1, 2, 3, 4, 5].map((d) => addDays(today, -d));
  return state;
}

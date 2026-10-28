/** Khớp com.cassin.vocab.DeckType */
export type DeckType = 'SYSTEM_DECK' | 'USER_DECK';

/** Khớp com.cassin.vocab.PartOfSpeech */
export type PartOfSpeech =
  | 'NOUN'
  | 'VERB'
  | 'ADJECTIVE'
  | 'ADVERB'
  | 'PREPOSITION'
  | 'PRONOUN'
  | 'CONJUNCTION'
  | 'INTERJECTION'
  | 'ARTICLE'
  | 'OTHER';

/**
 * Khớp DeckResponse.
 *
 * [CẦN BE BỔ SUNG] dueCount, newCount, updatedAt: giao diện cần hiện "12 thẻ đến hạn" cho từng bộ,
 * nhưng DeckResponse hiện chưa có. Đánh dấu optional (?) để khi nối API thật mà BE chưa thêm,
 * giao diện vẫn chạy (chỉ ẩn phần số liệu đó).
 */
export interface Deck {
  id: number;
  deckName: string;
  description: string | null;
  deckType: DeckType;
  cardCount: number;
  editable: boolean;
  dueCount?: number;
  newCount?: number;
  updatedAt?: string;
}

/** Khớp DeckRequest (dùng cho cả tạo và sửa) */
export interface DeckRequest {
  deckName: string;
  description: string | null;
}

/** Khớp CardResponse */
export interface Card {
  id: number;
  word: string;
  partOfSpeech: PartOfSpeech;
  phonetic: string | null;
  definition: string;
  examples: string[];
}

/** Khớp CardRequest */
export interface CardRequest {
  word: string;
  partOfSpeech: PartOfSpeech;
  phonetic: string | null;
  definition: string;
  examples: string[];
}

/** Giới hạn khớp với validation ở backend (Card.MAX_EXAMPLES, @Size...). */
export const CARD_LIMITS = {
  word: 100,
  phonetic: 100,
  definition: 255,
  example: 500,
  maxExamples: 5,
} as const;

export const DECK_LIMITS = {
  deckName: 100,
  description: 500,
} as const;

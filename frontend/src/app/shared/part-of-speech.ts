/** Loại từ: khớp enum PartOfSpeech ở backend. */
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

/** Tên viết tắt (như trong từ điển) và tên đầy đủ tiếng Việt. */
export const POS_LABELS: Record<PartOfSpeech, { short: string; full: string }> = {
  NOUN: { short: 'n.', full: 'danh từ' },
  VERB: { short: 'v.', full: 'động từ' },
  ADJECTIVE: { short: 'adj.', full: 'tính từ' },
  ADVERB: { short: 'adv.', full: 'trạng từ' },
  PREPOSITION: { short: 'prep.', full: 'giới từ' },
  PRONOUN: { short: 'pron.', full: 'đại từ' },
  CONJUNCTION: { short: 'conj.', full: 'liên từ' },
  INTERJECTION: { short: 'interj.', full: 'thán từ' },
  ARTICLE: { short: 'art.', full: 'mạo từ' },
  OTHER: { short: '', full: 'khác' },
};

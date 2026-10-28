import { PartOfSpeech } from '../models/deck.models';

/** Tên loại từ tiếng Việt + viết tắt kiểu từ điển. */
export const PART_OF_SPEECH: Record<PartOfSpeech, { label: string; short: string }> = {
  NOUN: { label: 'danh từ', short: 'n.' },
  VERB: { label: 'động từ', short: 'v.' },
  ADJECTIVE: { label: 'tính từ', short: 'adj.' },
  ADVERB: { label: 'trạng từ', short: 'adv.' },
  PREPOSITION: { label: 'giới từ', short: 'prep.' },
  PRONOUN: { label: 'đại từ', short: 'pron.' },
  CONJUNCTION: { label: 'liên từ', short: 'conj.' },
  INTERJECTION: { label: 'thán từ', short: 'interj.' },
  ARTICLE: { label: 'mạo từ', short: 'art.' },
  OTHER: { label: 'khác', short: '' },
};

export const PART_OF_SPEECH_OPTIONS = (Object.keys(PART_OF_SPEECH) as PartOfSpeech[]).map((value) => ({
  value,
  label: PART_OF_SPEECH[value].label,
}));

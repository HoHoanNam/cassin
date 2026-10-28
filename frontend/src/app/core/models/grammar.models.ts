/** Khớp com.cassin.grammar.CefrLevel */
export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';

export const CEFR_LEVELS: readonly CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

/** Khớp TopicSummary */
export interface TopicSummary {
  id: number;
  title: string;
  level: CefrLevel;
  questionCount: number;
}

/** Khớp TopicDetailResponse */
export interface TopicDetail extends TopicSummary {
  /** Bài giải thích lý thuyết. Các đoạn cách nhau bằng một dòng trống. */
  explanation: string;
}

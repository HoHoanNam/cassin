import { CefrLevel } from '../models/grammar.models';
import { AnswerKind, PassageStatus, TfngAnswer } from '../models/passage.models';

/**
 * [THÊM] Bài đọc mẫu cho chế độ mock. Mỗi bài đủ 3 dạng câu, có gợi ý bằng từ đồng nghĩa:
 * câu hỏi dùng một cách nói ("replacement"), bài dùng cách nói khác ("alternative").
 */
export type SeedQuestion =
  | { type: 'MCQ'; prompt: string; evidence: string; explanation: string; hints?: [string, string][]; options: string[]; correct: number }
  | { type: 'TFNG'; prompt: string; evidence: string; explanation: string; hints?: [string, string][]; answer: TfngAnswer }
  | {
      type: 'BLANK';
      prompt: string;
      evidence: string;
      explanation: string;
      hints?: [string, string][];
      answerKind: AnswerKind;
      maxWords: number | null;
      acceptedAnswers: string[];
    };

export interface SeedPassage {
  title: string;
  level: CefrLevel;
  status: PassageStatus;
  content: string;
  questions: SeedQuestion[];
}

export const SEED_PASSAGES: SeedPassage[] = [
  {
    title: 'Solar power in small towns',
    level: 'B1',
    status: 'PUBLISHED',
    content: `Solar power is now a cheap alternative to coal in many countries. Twenty years ago, solar panels were expensive and only rich families could afford them. Today, the price has fallen by almost ninety percent.

In the town of Green Valley, about 15% of homes used solar panels on their roofs in 2020. The local council offered small loans to families who wanted to install them. Many people said the loans were the main reason they changed.

However, solar power is not perfect. Panels produce less electricity in winter, and families still need another source of energy at night. Some scientists believe that better batteries will solve this problem within ten years.`,
    questions: [
      {
        type: 'BLANK',
        prompt: 'In many countries, solar power has become an inexpensive replacement for ____.',
        evidence: 'Solar power is now a cheap alternative to coal in many countries.',
        explanation: '"cheap alternative to coal" = "inexpensive replacement for coal".',
        hints: [['replacement', 'alternative'], ['inexpensive', 'cheap']],
        answerKind: 'WORDS',
        maxWords: 1,
        acceptedAnswers: ['coal'],
      },
      {
        type: 'TFNG',
        prompt: 'Most homes in Green Valley used solar panels in 2020.',
        evidence: 'In the town of Green Valley, about 15% of homes used solar panels on their roofs in 2020.',
        explanation: 'Chỉ khoảng 15% số nhà, không phải phần lớn (most) → False.',
        hints: [['Most homes', '15% of homes']],
        answer: 'FALSE',
      },
      {
        type: 'MCQ',
        prompt: 'Why did many families in Green Valley start using solar panels?',
        evidence: 'Many people said the loans were the main reason they changed.',
        explanation: 'Bài nói khoản vay nhỏ của hội đồng địa phương là lý do chính (main reason).',
        hints: [['Why', 'main reason']],
        options: ['Electricity became more expensive', 'The council lent them money', 'Their neighbours told them to', 'Coal was no longer available'],
        correct: 1,
      },
      {
        type: 'TFNG',
        prompt: 'Solar panels in Green Valley were made by a local company.',
        evidence: 'The local council offered small loans to families who wanted to install them.',
        explanation: 'Bài chỉ nói hội đồng cho vay tiền, không nói ai sản xuất tấm pin → Not Given.',
        answer: 'NOT_GIVEN',
      },
      {
        type: 'BLANK',
        prompt: 'Some scientists think improved batteries could fix the night-time problem within ____.',
        evidence: 'Some scientists believe that better batteries will solve this problem within ten years.',
        explanation: '"better batteries will solve this problem within ten years".',
        hints: [['improved', 'better'], ['fix', 'solve']],
        answerKind: 'WORDS_AND_OR_NUMBER',
        maxWords: 2,
        acceptedAnswers: ['ten years', '10 years'],
      },
    ],
  },
  {
    title: 'A morning at the market',
    level: 'A2',
    status: 'PUBLISHED',
    content: `Every Saturday, Lan goes to the market near her house. She usually leaves home at seven o'clock because the vegetables are fresh in the early morning.

Lan buys fruit from an old woman called Mrs Hoa. Mrs Hoa has sold fruit at the market for thirty years. Her mangoes are the most popular, so they are often gone before nine o'clock.

After shopping, Lan drinks a glass of sugarcane juice. It costs 10,000 dong, and she thinks it is the best drink in the city.`,
    questions: [
      {
        type: 'MCQ',
        prompt: 'Why does Lan go to the market early?',
        evidence: "She usually leaves home at seven o'clock because the vegetables are fresh in the early morning.",
        explanation: 'Rau tươi vào sáng sớm (fresh in the early morning).',
        hints: [['early', 'early morning']],
        options: ['The market closes at nine', 'The vegetables are fresh', 'She works in the afternoon', 'It is less crowded'],
        correct: 1,
      },
      {
        type: 'TFNG',
        prompt: 'Mrs Hoa has worked at the market for a long time.',
        evidence: 'Mrs Hoa has sold fruit at the market for thirty years.',
        explanation: '30 năm = a long time → True.',
        hints: [['a long time', 'thirty years']],
        answer: 'TRUE',
      },
      {
        type: 'BLANK',
        prompt: 'A glass of sugarcane juice costs ____ dong.',
        evidence: 'It costs 10,000 dong, and she thinks it is the best drink in the city.',
        explanation: 'Giá ghi rõ trong bài: 10,000 dong.',
        answerKind: 'NUMBER',
        maxWords: null,
        acceptedAnswers: ['10,000', '10000'],
      },
    ],
  },
  {
    title: 'Learning with podcasts (bản nháp)',
    level: 'B2',
    status: 'DRAFT',
    content: `Podcasts have become a popular way to practise listening. Learners can choose topics they enjoy, from science to football, and listen while travelling to work.`,
    questions: [],
  },
];

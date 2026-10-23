import { CefrLevel } from '../home/home.models';
import { QuizQuestion } from './quiz.models';

/**
 * Dữ liệu giả CÓ KÈM ĐÁP ÁN, chỉ để mock chấm bài thay server.
 * Với API thật, đáp án đúng không bao giờ được gửi xuống trước khi nộp bài.
 */
export interface MockQuestion extends QuizQuestion {
  correctOptionId: number;
  explanation: string;
}

export const QUIZ_MOCK: Record<number, { title: string; level: CefrLevel; questions: MockQuestion[] }> = {
  1: {
    title: 'Thì hiện tại hoàn thành',
    level: 'A2',
    questions: [
      {
        id: 11,
        content: 'She ___ in Hanoi since 2020.',
        options: [
          { id: 111, content: 'lives' },
          { id: 112, content: 'has lived' },
          { id: 113, content: 'lived' },
          { id: 114, content: 'is living' },
        ],
        correctOptionId: 112,
        explanation: 'Có "since 2020": việc bắt đầu trong quá khứ và vẫn kéo dài đến bây giờ, nên dùng hiện tại hoàn thành.',
      },
      {
        id: 12,
        content: 'I ___ this film three times.',
        options: [
          { id: 121, content: 'saw' },
          { id: 122, content: 'have seen' },
          { id: 123, content: 'see' },
          { id: 124, content: 'had seen' },
        ],
        correctOptionId: 122,
        explanation: 'Đếm số lần đã làm tính đến hiện tại, không nói rõ lúc nào: hiện tại hoàn thành.',
      },
      {
        id: 13,
        content: 'They ___ their homework yet.',
        options: [
          { id: 131, content: "haven't finished" },
          { id: 132, content: "didn't finish" },
          { id: 133, content: "don't finish" },
          { id: 134, content: "hadn't finished" },
        ],
        correctOptionId: 131,
        explanation: '"yet" ở cuối câu phủ định là dấu hiệu quen thuộc của hiện tại hoàn thành.',
      },
      {
        id: 14,
        content: 'We ___ to Da Nang last summer.',
        options: [
          { id: 141, content: 'have gone' },
          { id: 142, content: 'went' },
          { id: 143, content: 'have been' },
          { id: 144, content: 'go' },
        ],
        correctOptionId: 142,
        explanation: '"last summer" là mốc thời gian đã kết thúc, nên dùng quá khứ đơn, không dùng hiện tại hoàn thành.',
      },
      {
        id: 15,
        content: 'How long ___ English?',
        options: [
          { id: 151, content: 'did you learn' },
          { id: 152, content: 'have you learned' },
          { id: 153, content: 'do you learn' },
          { id: 154, content: 'you have learned' },
        ],
        correctOptionId: 152,
        explanation: '"How long" hỏi khoảng thời gian kéo dài đến hiện tại. Câu hỏi đảo "have" lên trước chủ ngữ.',
      },
    ],
  },
  2: {
    title: 'Câu bị động',
    level: 'B1',
    questions: [
      {
        id: 21,
        content: 'This bridge ___ in 1998.',
        options: [
          { id: 211, content: 'was built' },
          { id: 212, content: 'built' },
          { id: 213, content: 'has built' },
          { id: 214, content: 'is building' },
        ],
        correctOptionId: 211,
        explanation: 'Cây cầu không tự xây, nên dùng bị động. Mốc 1998 là quá khứ: was + V3.',
      },
      {
        id: 22,
        content: 'English ___ in many countries.',
        options: [
          { id: 221, content: 'speaks' },
          { id: 222, content: 'is spoken' },
          { id: 223, content: 'is speaking' },
          { id: 224, content: 'spoke' },
        ],
        correctOptionId: 222,
        explanation: 'Sự thật chung, chủ ngữ chịu tác động: hiện tại đơn bị động, is + V3.',
      },
      {
        id: 23,
        content: 'The letters ___ yet.',
        options: [
          { id: 231, content: "haven't been sent" },
          { id: 232, content: "didn't send" },
          { id: 233, content: "haven't sent" },
          { id: 234, content: "weren't sending" },
        ],
        correctOptionId: 231,
        explanation: '"yet" cần hiện tại hoàn thành, thư không tự gửi nên dùng bị động: have been + V3.',
      },
    ],
  },
};

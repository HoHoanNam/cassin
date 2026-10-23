import { DeckDetail } from './deck.models';

/** Dữ liệu giả cho trang chi tiết bộ từ. Xóa khi có API. */
export const DECK_MOCK: Record<number, DeckDetail> = {
  1: {
    id: 1,
    name: '1000 từ thông dụng',
    description: 'Những từ gặp nhiều nhất trong giao tiếp hằng ngày.',
    type: 'SYSTEM_DECK',
    dueCount: 12,
    cards: [
      { id: 100, word: 'light', partOfSpeech: 'NOUN', phonetic: '/laɪt/', definition: 'ánh sáng', examples: ['Turn on the light, please.'], box: 4 },
      { id: 101, word: 'light', partOfSpeech: 'ADJECTIVE', phonetic: '/laɪt/', definition: 'nhẹ', examples: ['This bag is light enough to carry on the plane.'], box: 2 },
      { id: 102, word: 'light', partOfSpeech: 'VERB', phonetic: '/laɪt/', definition: 'thắp, đốt', examples: ['She lit a candle.'], box: null },
      { id: 107, word: 'bank', partOfSpeech: 'NOUN', phonetic: '/bæŋk/', definition: 'ngân hàng', examples: ['I need to go to the bank.'], box: 5 },
      { id: 108, word: 'bank', partOfSpeech: 'NOUN', phonetic: '/bæŋk/', definition: 'bờ sông', examples: ['We had a picnic on the bank of the river.'], box: 3 },
      { id: 109, word: 'book', partOfSpeech: 'NOUN', phonetic: '/bʊk/', definition: 'quyển sách', examples: [], box: 5 },
      { id: 110, word: 'book', partOfSpeech: 'VERB', phonetic: '/bʊk/', definition: 'đặt chỗ, đặt trước', examples: ['I booked a table for two.'], box: 2 },
      { id: 111, word: 'fair', partOfSpeech: 'ADJECTIVE', phonetic: '/feə(r)/', definition: 'công bằng', examples: ["It's not fair!"], box: 3 },
      { id: 113, word: 'fair', partOfSpeech: 'NOUN', phonetic: '/feə(r)/', definition: 'hội chợ', examples: ['a book fair'], box: 1 },
      { id: 112, word: 'improve', partOfSpeech: 'VERB', phonetic: '/ɪmˈpruːv/', definition: 'cải thiện, tiến bộ', examples: ['Reading every day will improve your vocabulary.', 'Her English has improved a lot.'], box: 1 },
      { id: 115, word: 'although', partOfSpeech: 'CONJUNCTION', phonetic: '/ɔːlˈðəʊ/', definition: 'mặc dù', examples: ['Although it was raining, we went out.'], box: 5 },
    ],
  },
  2: {
    id: 2,
    name: 'Từ vựng IELTS Writing',
    description: 'Từ để mô tả biểu đồ và viết bài luận Task 2.',
    type: 'USER_DECK',
    dueCount: 6,
    cards: [
      { id: 201, word: 'significant', partOfSpeech: 'ADJECTIVE', phonetic: '/sɪɡˈnɪf.ɪ.kənt/', definition: 'đáng kể', examples: ['There was a significant rise in sales.'], box: 2 },
      { id: 205, word: 'decline', partOfSpeech: 'VERB', phonetic: '/dɪˈklaɪn/', definition: 'giảm, sụt xuống', examples: ['The number of visitors declined sharply in 2020.'], box: 1 },
      { id: 206, word: 'decline', partOfSpeech: 'NOUN', phonetic: '/dɪˈklaɪn/', definition: 'sự suy giảm', examples: ['a decline in birth rates'], box: 1 },
      { id: 207, word: 'consequently', partOfSpeech: 'ADVERB', phonetic: '/ˈkɒn.sɪ.kwənt.li/', definition: 'do đó, vì vậy', examples: ['He missed the bus and consequently arrived late.'], box: 4 },
      { id: 208, word: 'fluctuate', partOfSpeech: 'VERB', phonetic: '/ˈflʌk.tʃu.eɪt/', definition: 'dao động, lên xuống thất thường', examples: ['Prices fluctuated between $5 and $8.'], box: null },
    ],
  },
  3: {
    id: 3,
    name: 'Phrasal verbs hay gặp',
    description: null,
    type: 'USER_DECK',
    dueCount: 0,
    cards: [
      { id: 301, word: 'give up', partOfSpeech: 'VERB', phonetic: null, definition: 'từ bỏ', examples: ["Don't give up!"], box: 3 },
      { id: 302, word: 'look after', partOfSpeech: 'VERB', phonetic: null, definition: 'chăm sóc', examples: ['Can you look after my cat this weekend?'], box: 4 },
      { id: 303, word: 'put off', partOfSpeech: 'VERB', phonetic: null, definition: 'trì hoãn', examples: ['Stop putting off your homework.'], box: 2 },
    ],
  },
};

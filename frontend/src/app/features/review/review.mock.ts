import { ReviewCard } from './review.models';

/** Dữ liệu giả: các thẻ đến hạn hôm nay. Xóa khi có API. */
export const REVIEW_MOCK: ReviewCard[] = [
  {
    cardId: 101,
    word: 'light',
    partOfSpeech: 'ADJECTIVE',
    phonetic: '/laɪt/',
    definition: 'nhẹ',
    examples: ['This bag is light enough to carry on the plane.'],
    box: 2,
  },
  {
    cardId: 205,
    word: 'decline',
    partOfSpeech: 'VERB',
    phonetic: '/dɪˈklaɪn/',
    definition: 'giảm, sụt xuống',
    examples: ['The number of visitors declined sharply in 2020.'],
    box: 1,
  },
  {
    cardId: 108,
    word: 'bank',
    partOfSpeech: 'NOUN',
    phonetic: '/bæŋk/',
    definition: 'bờ sông',
    examples: ['We had a picnic on the bank of the river.'],
    box: 3,
  },
  {
    cardId: 207,
    word: 'consequently',
    partOfSpeech: 'ADVERB',
    phonetic: '/ˈkɒn.sɪ.kwənt.li/',
    definition: 'do đó, vì vậy',
    examples: ['He missed the bus and consequently arrived late.'],
    box: 4,
  },
  {
    cardId: 112,
    word: 'improve',
    partOfSpeech: 'VERB',
    phonetic: '/ɪmˈpruːv/',
    definition: 'cải thiện, tiến bộ',
    examples: ['Reading every day will improve your vocabulary.', 'Her English has improved a lot.'],
    box: 1,
  },
  {
    cardId: 115,
    word: 'although',
    partOfSpeech: 'CONJUNCTION',
    phonetic: '/ɔːlˈðəʊ/',
    definition: 'mặc dù',
    examples: ['Although it was raining, we went out.'],
    box: 5,
  },
];

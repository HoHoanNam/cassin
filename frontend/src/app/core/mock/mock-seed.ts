/**
 * Dữ liệu mẫu cho chế độ mock. Chỉ để làm giao diện khi chưa nối backend;
 * khi chạy thật, nội dung lấy từ database (migration V9__seed_content.sql, sinh ra từ chính file này).
 */
import { CefrLevel } from '../models/grammar.models';
import { PartOfSpeech } from '../models/deck.models';

/** [từ, loại từ, phiên âm, nghĩa, câu ví dụ] */
export type SeedCard = [string, PartOfSpeech, string | null, string, string[]];

export const SEED_SYSTEM_DECK = {
  deckName: '1000 từ thông dụng',
  description: 'Những từ xuất hiện nhiều nhất trong giao tiếp và bài đọc hằng ngày.',
  cards: [
    ['improve', 'VERB', '/ɪmˈpruːv/', 'cải thiện, tiến bộ', ['Her English has improved a lot.']],
    ['achieve', 'VERB', '/əˈtʃiːv/', 'đạt được', ['She achieved her goal of running a marathon.']],
    ['opportunity', 'NOUN', '/ˌɒpəˈtjuːnəti/', 'cơ hội', ['This job is a great opportunity for you.']],
    ['environment', 'NOUN', '/ɪnˈvaɪrənmənt/', 'môi trường', ['We must protect the environment.']],
    ['necessary', 'ADJECTIVE', '/ˈnesəsəri/', 'cần thiết', ['Is it necessary to book in advance?']],
    ['decide', 'VERB', '/dɪˈsaɪd/', 'quyết định', ['They decided to stay at home.']],
    ['experience', 'NOUN', '/ɪkˈspɪəriəns/', 'kinh nghiệm', ['Do you have any teaching experience?']],
    ['experience', 'VERB', '/ɪkˈspɪəriəns/', 'trải qua', ['The city experienced heavy rain last week.']],
    ['light', 'NOUN', '/laɪt/', 'ánh sáng', ['Turn on the light, please.']],
    ['light', 'ADJECTIVE', '/laɪt/', 'nhẹ', ['This bag is light enough to carry.']],
    ['although', 'CONJUNCTION', '/ɔːlˈðəʊ/', 'mặc dù', ['Although it was raining, we went out.']],
    ['probably', 'ADVERB', '/ˈprɒbəbli/', 'có lẽ, chắc là', ['He will probably be late.']],
    ['borrow', 'VERB', '/ˈbɒrəʊ/', 'mượn', ['Can I borrow your pen?']],
    ['knowledge', 'NOUN', '/ˈnɒlɪdʒ/', 'kiến thức', ['Reading improves your knowledge.']],
    ['careful', 'ADJECTIVE', '/ˈkeəfl/', 'cẩn thận', ['Be careful when you cross the road.']],
    ['between', 'PREPOSITION', '/bɪˈtwiːn/', 'giữa (hai vật)', ['The bank is between the café and the school.']],
    ['remember', 'VERB', '/rɪˈmembə(r)/', 'nhớ', ['I can’t remember his name.']],
    ['healthy', 'ADJECTIVE', '/ˈhelθi/', 'khỏe mạnh, tốt cho sức khỏe', ['Fruit is part of a healthy diet.']],
    ['suddenly', 'ADVERB', '/ˈsʌdənli/', 'đột nhiên', ['Suddenly, the lights went out.']],
    ['support', 'VERB', '/səˈpɔːt/', 'ủng hộ, hỗ trợ', ['My parents always support me.']],
    ['journey', 'NOUN', '/ˈdʒɜːni/', 'chuyến đi, hành trình', ['The journey takes three hours.']],
    ['enough', 'ADVERB', '/ɪˈnʌf/', 'đủ', ['He isn’t old enough to drive.']],
    ['explain', 'VERB', '/ɪkˈspleɪn/', 'giải thích', ['Can you explain this word to me?']],
    ['familiar', 'ADJECTIVE', '/fəˈmɪliə(r)/', 'quen thuộc', ['Her face looks familiar.']],
  ] as SeedCard[],
};

export const SEED_USER_DECKS = [
  {
    deckName: 'Từ vựng IELTS Writing',
    description: 'Từ nối và từ học thuật cho Writing Task 2.',
    cards: [
      ['significant', 'ADJECTIVE', '/sɪɡˈnɪfɪkənt/', 'đáng kể, quan trọng', ['There has been a significant increase in prices.']],
      ['moreover', 'ADVERB', '/mɔːrˈəʊvə(r)/', 'hơn nữa', ['The plan is cheap. Moreover, it is easy to carry out.']],
      ['consequence', 'NOUN', '/ˈkɒnsɪkwəns/', 'hậu quả', ['Pollution has serious consequences for health.']],
      ['beneficial', 'ADJECTIVE', '/ˌbenɪˈfɪʃl/', 'có lợi', ['Regular exercise is beneficial to everyone.']],
      ['tackle', 'VERB', '/ˈtækl/', 'giải quyết (vấn đề khó)', ['The government must tackle unemployment.']],
      ['whereas', 'CONJUNCTION', '/ˌweərˈæz/', 'trong khi (đối lập)', ['Some people like tea, whereas others prefer coffee.']],
      ['inevitable', 'ADJECTIVE', '/ɪnˈevɪtəbl/', 'không thể tránh khỏi', ['Change is inevitable.']],
      ['contribute', 'VERB', '/kənˈtrɪbjuːt/', 'đóng góp', ['Tourism contributes to the local economy.']],
      ['crucial', 'ADJECTIVE', '/ˈkruːʃl/', 'cực kỳ quan trọng', ['Sleep is crucial for learning.']],
      ['tendency', 'NOUN', '/ˈtendənsi/', 'xu hướng', ['There is a tendency to work longer hours.']],
      ['alleviate', 'VERB', '/əˈliːvieɪt/', 'làm giảm bớt', ['New roads may alleviate traffic jams.']],
      ['nevertheless', 'ADVERB', '/ˌnevəðəˈles/', 'tuy nhiên', ['It was hard. Nevertheless, we finished on time.']],
    ] as SeedCard[],
  },
  {
    deckName: 'Phrasal verbs hay gặp',
    description: null,
    cards: [
      ['give up', 'VERB', null, 'từ bỏ', ['Don’t give up on your dreams.']],
      ['look after', 'VERB', null, 'chăm sóc', ['She looks after her little brother.']],
      ['find out', 'VERB', null, 'tìm ra, phát hiện', ['I found out the truth yesterday.']],
      ['put off', 'VERB', null, 'trì hoãn', ['Stop putting off your homework.']],
      ['run out of', 'VERB', null, 'hết, cạn', ['We have run out of milk.']],
      ['take after', 'VERB', null, 'giống (người lớn trong nhà)', ['He takes after his father.']],
      ['turn down', 'VERB', null, 'từ chối', ['She turned down the job offer.']],
      ['come across', 'VERB', null, 'tình cờ gặp / thấy', ['I came across an old photo.']],
    ] as SeedCard[],
  },
];

/** [đề, lời giải thích, 4 đáp án, vị trí đáp án đúng] */
export type SeedQuestion = [string, string, [string, string, string, string], number];

export interface SeedTopic {
  title: string;
  level: CefrLevel;
  explanation: string;
  questions: SeedQuestion[];
}

export const SEED_TOPICS: SeedTopic[] = [
  {
    title: 'Thì hiện tại hoàn thành',
    level: 'A2',
    explanation: `Dùng thì hiện tại hoàn thành khi một việc xảy ra trong quá khứ nhưng vẫn còn liên quan tới hiện tại: kết quả còn đó, hoặc việc vẫn đang tiếp diễn.

Cấu trúc: S + have/has + V3 (quá khứ phân từ).
• I/You/We/They + have + V3
• He/She/It + has + V3

Dấu hiệu thường gặp: since (kể từ một mốc), for (trong một khoảng), already, yet, ever, never, just, so far.

Ví dụ: She has lived in Hanoi since 2020. (Cô ấy sống ở Hà Nội từ 2020 và bây giờ vẫn sống ở đó.)

Phân biệt với quá khứ đơn: có mốc thời gian đã kết thúc (yesterday, last year, in 2019) thì dùng quá khứ đơn, không dùng hiện tại hoàn thành.`,
    questions: [
      ['She ___ in Hanoi since 2020.', 'Có "since" + mốc thời gian, việc vẫn còn tiếp diễn → hiện tại hoàn thành: has + V3.', ['has lived', 'lived', 'lives', 'is living'], 0],
      ['I ___ this film twice.', 'Nói về số lần trải nghiệm tính tới hiện tại → hiện tại hoàn thành.', ['saw', 'have seen', 'see', 'am seeing'], 1],
      ['They ___ their homework yet.', '"yet" trong câu phủ định → hiện tại hoàn thành: haven’t + V3.', ['didn’t finish', 'don’t finish', 'haven’t finished', 'aren’t finishing'], 2],
      ['___ you ever ___ to Da Lat?', '"ever" hỏi về trải nghiệm → Have you ever + V3?', ['Did / go', 'Have / been', 'Do / go', 'Are / going'], 1],
      ['He ___ his keys, so he can’t open the door.', 'Kết quả còn ở hiện tại (không mở được cửa) → hiện tại hoàn thành.', ['has lost', 'lost', 'loses', 'was losing'], 0],
      ['We ___ each other for ten years.', '"for" + khoảng thời gian, quan hệ vẫn còn → hiện tại hoàn thành.', ['know', 'knew', 'are knowing', 'have known'], 3],
      ['I ___ to the cinema last night.', '"last night" là mốc đã kết thúc → quá khứ đơn, không dùng hiện tại hoàn thành.', ['have gone', 'went', 'have been going', 'go'], 1],
      ['She has ___ finished her report.', '"just" (vừa mới) đứng giữa has và V3.', ['yet', 'ever', 'just', 'since'], 2],
    ],
  },
  {
    title: 'Câu bị động',
    level: 'B1',
    explanation: `Dùng câu bị động khi muốn nhấn mạnh việc được làm hoặc đối tượng chịu tác động, hơn là người làm.

Cấu trúc chung: S + be (chia theo thì) + V3 (+ by + người làm).
• Hiện tại đơn: is/are + V3 → English is spoken here.
• Quá khứ đơn: was/were + V3 → This bridge was built in 1998.
• Hiện tại hoàn thành: has/have been + V3 → The letter has been sent.
• Với động từ khuyết thiếu: can/must/will + be + V3 → It must be done today.

Chỉ thêm "by + người làm" khi thông tin đó quan trọng.`,
    questions: [
      ['This bridge ___ in 1998.', 'Cây cầu không tự xây, có mốc quá khứ → bị động quá khứ đơn: was + V3.', ['was built', 'built', 'has built', 'is building'], 0],
      ['English ___ in many countries.', 'Sự thật chung, ngôn ngữ "được nói" → is spoken.', ['speaks', 'is spoken', 'spoke', 'is speaking'], 1],
      ['The letter ___ yet.', '"yet" + bị động → hasn’t been + V3.', ['didn’t send', 'isn’t sent', 'hasn’t been sent', 'wasn’t sending'], 2],
      ['This work must ___ before Friday.', 'Sau động từ khuyết thiếu: be + V3.', ['finish', 'be finished', 'finished', 'be finishing'], 1],
      ['The thief ___ by the police last night.', 'Mốc "last night", tên trộm bị bắt → was caught.', ['caught', 'is caught', 'was caught', 'has caught'], 2],
      ['Rice ___ in the Mekong Delta.', 'Sự thật chung, lúa "được trồng" → is grown.', ['grows', 'is grown', 'grew', 'is growing'], 1],
      ['A new hospital ___ next year.', 'Tương lai + bị động → will be + V3.', ['will build', 'will be built', 'is building', 'builds'], 1],
      ['The windows ___ every week.', 'Thói quen lặp lại, cửa sổ "được lau" → are cleaned.', ['clean', 'cleaned', 'are cleaned', 'are cleaning'], 2],
    ],
  },
  {
    title: 'Câu điều kiện loại 1 và 2',
    level: 'B1',
    explanation: `Loại 1: điều kiện CÓ THỂ xảy ra ở hiện tại hoặc tương lai.
If + hiện tại đơn, will + V.
Ví dụ: If it rains, we will stay at home.

Loại 2: điều kiện KHÔNG CÓ THẬT hoặc khó xảy ra ở hiện tại.
If + quá khứ đơn, would + V.
Ví dụ: If I had more time, I would learn French.

Lưu ý: ở loại 2, động từ "be" thường dùng "were" cho mọi chủ ngữ: If I were you, I would rest.`,
    questions: [
      ['If it ___ tomorrow, we will cancel the trip.', 'Loại 1: mệnh đề if dùng hiện tại đơn.', ['rains', 'will rain', 'rained', 'would rain'], 0],
      ['If I ___ rich, I would travel the world.', 'Loại 2: không có thật ở hiện tại → quá khứ đơn, "be" dùng were.', ['am', 'will be', 'were', 'have been'], 2],
      ['If you heat ice, it ___.', 'Sự thật luôn đúng: điều kiện + kết quả đều ở hiện tại đơn.', ['melts', 'would melt', 'melted', 'has melted'], 0],
      ['She ___ the bus if she leaves now.', 'Loại 1: mệnh đề chính dùng will + V.', ['would catch', 'caught', 'will catch', 'catches'], 2],
      ['If I were you, I ___ that job.', 'Lời khuyên với "If I were you" → would + V.', ['will take', 'would take', 'take', 'took'], 1],
      ['What would you do if you ___ a snake?', 'Loại 2 (tình huống giả định) → quá khứ đơn.', ['see', 'will see', 'saw', 'have seen'], 2],
    ],
  },
  {
    title: 'Quá khứ đơn và quá khứ tiếp diễn',
    level: 'A2',
    explanation: `Quá khứ tiếp diễn (was/were + V-ing) diễn tả một việc ĐANG xảy ra tại một thời điểm trong quá khứ.
Quá khứ đơn diễn tả một việc đã xảy ra và kết thúc.

Khi một việc đang diễn ra thì một việc khác xen vào:
• Việc đang diễn ra → quá khứ tiếp diễn
• Việc xen vào → quá khứ đơn
Ví dụ: I was cooking when the phone rang.`,
    questions: [
      ['I ___ dinner when the phone rang.', 'Việc đang diễn ra bị việc khác xen vào → was cooking.', ['cooked', 'was cooking', 'cook', 'have cooked'], 1],
      ['At 8 p.m. yesterday, they ___ TV.', 'Thời điểm cụ thể trong quá khứ → quá khứ tiếp diễn.', ['watched', 'were watching', 'watch', 'have watched'], 1],
      ['She ___ her leg while she was skiing.', 'Việc xen vào (gãy chân) → quá khứ đơn.', ['broke', 'was breaking', 'breaks', 'has broken'], 0],
      ['While I ___ to work, I saw an accident.', '"While" + việc đang diễn ra → was walking.', ['walked', 'walk', 'was walking', 'have walked'], 2],
    ],
  },
  {
    title: 'Mệnh đề quan hệ',
    level: 'B2',
    explanation: `Mệnh đề quan hệ bổ nghĩa cho danh từ đứng trước nó.
• who: thay cho người
• which: thay cho vật
• that: thay cho cả người và vật (không dùng sau dấu phẩy)
• whose: chỉ sở hữu
• where: chỉ nơi chốn

Ví dụ: The woman who lives next door is a doctor.`,
    questions: [
      ['The man ___ called you is my uncle.', 'Thay cho người, làm chủ ngữ → who.', ['which', 'who', 'whose', 'where'], 1],
      ['This is the house ___ I was born.', 'Chỉ nơi chốn → where.', ['which', 'who', 'where', 'whose'], 2],
      ['I have a friend ___ father is a pilot.', 'Sở hữu (bố CỦA bạn ấy) → whose.', ['who', 'whose', 'which', 'that'], 1],
      ['My phone, ___ I bought last year, is broken.', 'Sau dấu phẩy, thay cho vật → which (không dùng that).', ['that', 'who', 'which', 'where'], 2],
    ],
  },
  {
    title: 'Câu tường thuật',
    level: 'B2',
    explanation: `Câu tường thuật thuật lại lời người khác nói. Khi động từ tường thuật ở quá khứ (said, told), thì của câu thường lùi một bậc: hiện tại đơn → quá khứ đơn, will → would...

Bộ câu hỏi cho chủ đề này đang được biên soạn.`,
    questions: [],
  },
];

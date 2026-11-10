/**
 * Bản TypeScript của com.cassin.scan (Tokenizer, Lemmatizer, StopWords) để chế độ MOCK quét được
 * đoạn văn ngay trong trình duyệt. Logic giống hệt backend: sửa một bên thì sửa cả bên kia.
 * Chỉ dùng trong core/mock; FE thật gọi POST /api/scan.
 */

// ===================== StopWords =====================

const STOP_WORDS = new Set<string>([
  "a", "an", "the", "this", "that", "these", "those", "some", "any", "each", "every", "no",
  "all", "both", "either", "neither", "much", "many", "more", "most", "few", "less", "other", "another",
  "such", "own", "i", "me", "my", "mine", "myself", "you", "your", "yours", "yourself", "yourselves",
  "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "we",
  "us", "our", "ours", "ourselves", "they", "them", "their", "theirs", "themselves", "who", "whom", "whose",
  "which", "what", "one", "am", "is", "are", "was", "were", "be", "been", "being", "have",
  "has", "had", "having", "do", "does", "did", "doing", "done", "will", "would", "shall", "should",
  "can", "could", "may", "might", "must", "of", "in", "on", "at", "by", "for", "with",
  "about", "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from",
  "up", "down", "out", "off", "over", "under", "again", "around", "near", "onto", "upon", "within",
  "without", "across", "along", "behind", "beyond", "since", "until", "till", "toward", "towards", "via", "and",
  "but", "or", "nor", "so", "yet", "if", "then", "than", "because", "as", "while", "when",
  "where", "why", "how", "although", "though", "whether", "not", "only", "very", "too", "also", "just",
  "here", "there", "now", "ever", "never", "still", "already", "even", "else", "once", "i'm", "i've",
  "i'll", "i'd", "you're", "you've", "you'll", "you'd", "he's", "he'll", "he'd", "she's", "she'll", "she'd",
  "it's", "it'll", "we're", "we've", "we'll", "we'd", "they're", "they've", "they'll", "they'd", "that's", "there's",
  "here's", "what's", "who's", "let's", "isn't", "aren't", "wasn't", "weren't", "don't", "doesn't", "didn't", "haven't",
  "hasn't", "hadn't", "won't", "wouldn't", "can't", "cannot", "couldn't", "shouldn't", "mustn't", "yes", "ok", "okay",
  "oh", "mr", "mrs", "ms", "etc",
]);

export const isStopWord = (lower: string) => STOP_WORDS.has(lower);

// ===================== Tokenizer =====================

export interface ScanToken {
  lower: string;
  sentenceIndex: number;
  start: number;
  end: number;
  upper: boolean;
  sentenceStart: boolean;
}

export interface Tokenized {
  sentences: string[];
  tokens: ScanToken[];
}

/** Sau . ! ? (kèm dấu nháy / ngoặc đóng) rồi khoảng trắng, hoặc một dòng trống. Xuống dòng đơn không tách câu. */
const SENTENCE_END = /(?<=[.!?]["'”’)]?)\s+|(?:\r\n|\r|\n)\s*(?:\r\n|\r|\n)/;
const WORD = /[A-Za-z]+(?:['’][A-Za-z]+)?/g;

export function tokenize(text: string): Tokenized {
  const sentences: string[] = [];
  const tokens: ScanToken[] = [];
  for (const raw of text.split(SENTENCE_END)) {
    const sentence = raw.trim().replace(/\s+/g, ' ');
    if (!sentence) {
      continue;
    }
    const index = sentences.length;
    sentences.push(sentence);
    let first = true;
    for (const m of sentence.matchAll(WORD)) {
      let lower = m[0].toLowerCase().replace(/’/g, "'");
      if (lower.endsWith("'s") && !STOP_WORDS.has(lower)) {
        lower = lower.slice(0, -2);
      }
      const start = m.index ?? 0;
      tokens.push({ lower, sentenceIndex: index, start, end: start + m[0].length, upper: /[A-Z]/.test(m[0][0]), sentenceStart: first });
      first = false;
    }
  }
  return { sentences, tokens };
}

// ===================== Lemmatizer =====================

const IRREGULAR: Record<string, string> = {
  went: 'go', gone: 'go', goes: 'go', ran: 'run', came: 'come', became: 'become', began: 'begin', begun: 'begin',
  broke: 'break', broken: 'break', brought: 'bring', built: 'build', bought: 'buy', caught: 'catch', chose: 'choose', chosen: 'choose',
  drew: 'draw', drawn: 'draw', drank: 'drink', drunk: 'drink', drove: 'drive', driven: 'drive', ate: 'eat', eaten: 'eat',
  fell: 'fall', fallen: 'fall', felt: 'feel', fought: 'fight', found: 'find', flew: 'fly', flown: 'fly', forgot: 'forget',
  forgotten: 'forget', gave: 'give', given: 'give', got: 'get', gotten: 'get', grew: 'grow', grown: 'grow', heard: 'hear',
  held: 'hold', hid: 'hide', hidden: 'hide', kept: 'keep', knew: 'know', known: 'know', laid: 'lay', led: 'lead',
  left: 'leave', lent: 'lend', lost: 'lose', made: 'make', meant: 'mean', met: 'meet', paid: 'pay', rode: 'ride',
  ridden: 'ride', rang: 'ring', rose: 'rise', risen: 'rise', said: 'say', saw: 'see', seen: 'see', sold: 'sell',
  sent: 'send', shook: 'shake', shot: 'shoot', showed: 'show', shown: 'show', sang: 'sing', sung: 'sing', sat: 'sit',
  slept: 'sleep', spoke: 'speak', spoken: 'speak', spent: 'spend', stood: 'stand', stole: 'steal', stolen: 'steal', swam: 'swim',
  took: 'take', taken: 'take', taught: 'teach', tore: 'tear', torn: 'tear', told: 'tell', thought: 'think', threw: 'throw',
  thrown: 'throw', understood: 'understand', woke: 'wake', woken: 'wake', wore: 'wear', worn: 'wear', won: 'win', wrote: 'write',
  written: 'write', fed: 'feed', fled: 'flee', bit: 'bite', bitten: 'bite', blew: 'blow', blown: 'blow', dealt: 'deal',
  dug: 'dig', froze: 'freeze', frozen: 'freeze', hung: 'hang', lit: 'light', slid: 'slide', stuck: 'stick', struck: 'strike',
  swore: 'swear', sworn: 'swear', wept: 'weep', lay: 'lie', lain: 'lie', children: 'child', men: 'man', women: 'woman',
  people: 'person', feet: 'foot', teeth: 'tooth', mice: 'mouse', geese: 'goose', lives: 'life', wives: 'wife', knives: 'knife',
  leaves: 'leaf', halves: 'half', shelves: 'shelf', wolves: 'wolf', thieves: 'thief', better: 'good', best: 'good', worse: 'bad',
  worst: 'bad',
};

const KEEP = new Set<string>([
  'news', 'bus', 'gas', 'yes', 'this', 'his', 'is', 'was', 'has', 'always',
  'perhaps', 'series', 'species', 'physics', 'mathematics', 'economics', 'politics', 'means', 'lens', 'plus',
  'status', 'virus', 'campus', 'bonus', 'focus', 'chaos', 'crisis', 'basis', 'analysis', 'thesis',
  'morning', 'evening', 'nothing', 'something', 'anything', 'everything', 'thing', 'king', 'ring', 'sing',
  'bring', 'spring', 'string', 'wing', 'swing', 'during', 'ceiling', 'building', 'meaning', 'feeling',
  'wedding', 'pudding', 'need', 'speed', 'seed', 'feed', 'bed', 'red', 'shed', 'hundred',
  'sacred', 'naked', 'wicked',
]);

const isVowel = (c: string) => 'aeiouy'.includes(c);
const endsWithAny = (s: string, ...suffixes: string[]) => suffixes.some((x) => s.endsWith(x));

function isDoubled(s: string): boolean {
  const n = s.length;
  return n >= 3 && s[n - 1] === s[n - 2] && !isVowel(s[n - 1]) && !'lsz'.includes(s[n - 1]);
}

function vowelGroups(s: string): number {
  let groups = 0;
  let inVowel = false;
  for (const c of s) {
    const v = isVowel(c);
    if (v && !inVowel) groups++;
    inVowel = v;
  }
  return groups;
}

function needsE(s: string): boolean {
  if (endsWithAny(s, 'v', 'z', 'c', 'dg', 'rg')) {
    return true;
  }
  const n = s.length;
  if (n < 2) {
    return false;
  }
  const last = s[n - 1];
  const prev = s[n - 2];
  const cvc = !isVowel(last) && !'wxy'.includes(last) && isVowel(prev) && (n === 2 || !isVowel(s[n - 3]));
  return cvc && vowelGroups(s) === 1;
}

function restoreStem(stem: string): string {
  if (isDoubled(stem)) return stem.slice(0, -1);
  if (stem.endsWith('e')) return stem + 'e';
  if (needsE(stem)) return stem + 'e';
  return stem;
}

function addStemVariants(out: Set<string>, stem: string): void {
  out.add(stem);
  out.add(stem + 'e');
  if (isDoubled(stem) || stem.endsWith('ll')) out.add(stem.slice(0, -1));
}

/** Các dạng có thể là gốc, theo thứ tự ưu tiên; phần tử đầu luôn là chính word. */
export function candidates(word: string): string[] {
  const out = new Set<string>([word]);
  const irregular = IRREGULAR[word];
  if (irregular) out.add(irregular);
  if (!KEEP.has(word)) {
    const n = word.length;
    if (word.endsWith('ies') && n > 4) out.add(word.slice(0, -3) + 'y');
    if (word.endsWith('es') && n > 3) out.add(word.slice(0, -2));
    if (word.endsWith('s') && !word.endsWith('ss') && n > 3) out.add(word.slice(0, -1));
    if (word.endsWith('ied') && n > 4) out.add(word.slice(0, -3) + 'y');
    if (word.endsWith('ed') && n > 3) addStemVariants(out, word.slice(0, -2));
    if (word.endsWith('ing') && n > 4) {
      addStemVariants(out, word.slice(0, -3));
      if (word.endsWith('ying')) out.add(word.slice(0, -4) + 'ie');
    }
  }
  return [...out].filter((s) => s.length >= 2);
}

/** Dạng gốc đoán được khi không có trong DB. */
export function guess(word: string): string {
  const irregular = IRREGULAR[word];
  if (irregular) return irregular;
  if (KEEP.has(word)) return word;
  const n = word.length;
  if ((word.endsWith('ies') || word.endsWith('ied')) && n > 4) return word.slice(0, -3) + 'y';
  if (word.endsWith('ying') && n === 5) return word[0] + 'ie';
  if (word.endsWith('ing') && n > 4) return restoreStem(word.slice(0, -3));
  if (word.endsWith('ed') && n > 3) return restoreStem(word.slice(0, -2));
  if (word.endsWith('es') && n > 4 && endsWithAny(word.slice(0, -2), 's', 'x', 'z', 'ch', 'sh', 'o')) return word.slice(0, -2);
  if (word.endsWith('s') && n > 3 && !endsWithAny(word, 'ss', 'us', 'is')) return word.slice(0, -1);
  return word;
}

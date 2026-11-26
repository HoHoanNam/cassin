#!/usr/bin/env node
/**
 * Nạp dataset (ngữ pháp, đọc hiểu, bộ từ) vào Cassin qua ADMIN API.
 *
 *   node scripts/load-dataset.mjs --dry-run                       # chỉ kiểm tra file, không gọi API
 *   node scripts/load-dataset.mjs --email admin@x.com --password ...
 *   node scripts/load-dataset.mjs --only vocab --api http://localhost:8080
 *
 * Tham số (hoặc biến môi trường):
 *   --dir        thư mục dataset            (mặc định: <repo>/dataset)
 *   --api        địa chỉ backend            (CASSIN_API, mặc định http://localhost:8080)
 *   --email      tài khoản admin            (CASSIN_ADMIN_EMAIL)
 *   --password   mật khẩu admin             (CASSIN_ADMIN_PASSWORD)
 *   --only       grammar,reading,vocab      (mặc định: cả 3)
 *   --dry-run    kiểm tra dữ liệu rồi dừng
 *
 * Vì sao gọi API mà không ghi thẳng SQL / Flyway?
 *   - dataset KHÔNG nằm trong repo (.gitignore), migration thì bắt buộc phải nằm trong repo
 *   - đi qua API = đi qua đúng validate của entity (evidence có trong bài, đúng 1 đáp án đúng...)
 *   - chạy được với mọi môi trường chỉ cần URL + tài khoản admin
 *
 * Chạy lại nhiều lần an toàn (idempotent):
 *   grammar : khớp chủ đề theo TIÊU ĐỀ → cập nhật lý thuyết, chỉ thêm câu hỏi chưa có (so nội dung câu)
 *   reading : khớp bài theo TIÊU ĐỀ → đã có thì bỏ qua; chưa có thì tạo + publish
 *   vocab   : khớp bộ hệ thống theo TÊN; từ đã có trong bộ thì bỏ qua; từ hệ thống đã tồn tại
 *             (409 WORD_EXISTS) thì GẮN từ có sẵn vào bộ thay vì tạo trùng
 *
 * Không cần cài gì thêm: Node 18+ (có sẵn fetch).
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// ============================== tham số ==============================

const args = parseArgs(process.argv.slice(2));
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DIR = resolve(args.dir ?? join(ROOT, 'dataset'));
const API = (args.api ?? process.env.CASSIN_API ?? 'http://localhost:8080').replace(/\/$/, '');
const ONLY = new Set((args.only ?? 'grammar,reading,vocab').split(',').map((s) => s.trim()));
const DRY_RUN = Boolean(args['dry-run']);

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i].replace(/^--/, '');
    const next = argv[i + 1];
    if (next === undefined || next.startsWith('--')) {
      out[key] = true;
    } else {
      out[key] = next;
      i++;
    }
  }
  return out;
}

// ============================== hằng số khớp backend ==============================

const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
const POS = ['NOUN', 'VERB', 'ADJECTIVE', 'ADVERB', 'PREPOSITION', 'PRONOUN', 'CONJUNCTION',
  'INTERJECTION', 'ARTICLE', 'IDIOM', 'PHRASAL_VERB', 'OTHER'];
const TFNG = ['TRUE', 'FALSE', 'NOT_GIVEN'];
const ANSWER_KINDS = ['WORDS', 'NUMBER', 'WORDS_AND_OR_NUMBER'];
const BLANK = '____';
const LIMITS = {
  topicTitle: 150, option: 255,
  passageTitle: 150, passageContent: 5000, passageQuestions: 20, hints: 3, hintPhrase: 100, accepted: 5, answer: 100,
  term: 100, phonetic: 100, senses: 10, definition: 255, examples: 5, example: 500,
  deckName: 100, deckDescription: 500,
  minTokens: 5, maxTokens: 15, // PracticeService: câu dùng cho Xếp câu
};

// ============================== đọc file ==============================

function readJsonFiles(sub) {
  const dir = join(DIR, sub);
  if (!existsSync(dir)) {
    return [];
  }
  return readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => {
      const path = join(sub, f);
      try {
        return { path, data: JSON.parse(readFileSync(join(dir, f), 'utf8')) };
      } catch (e) {
        return { path, error: `JSON lỗi: ${e.message}` };
      }
    });
}

// ============================== kiểm tra dữ liệu ==============================

class Report {
  errors = [];
  warnings = [];
  error(where, msg) { this.errors.push(`${where}: ${msg}`); }
  warn(where, msg) { this.warnings.push(`${where}: ${msg}`); }
}

const isBlank = (s) => typeof s !== 'string' || s.trim() === '';
const tooLong = (s, max) => typeof s === 'string' && s.length > max;
const words = (s) => s.trim().split(/\s+/).filter(Boolean).length;

function checkGrammar(file, r) {
  const { path, data } = file;
  if (!LEVELS.includes(data.level)) r.error(path, `level "${data.level}" không hợp lệ`);
  const stats = { topics: 0, questions: 0 };
  const titles = new Set();
  (data.topics ?? []).forEach((t, ti) => {
    const at = `${path} › ${t.title ?? `topic #${ti + 1}`}`;
    stats.topics++;
    if (isBlank(t.title) || tooLong(t.title, LIMITS.topicTitle)) r.error(at, `tiêu đề trống hoặc dài quá ${LIMITS.topicTitle}`);
    if (titles.has(t.title)) r.error(at, 'trùng tiêu đề trong cùng file');
    titles.add(t.title);
    if (isBlank(t.explanation)) r.error(at, 'thiếu lý thuyết');
    // topic-page gom dòng thường lên TRƯỚC các gạch đầu dòng trong cùng đoạn → dòng thường sau "• " sẽ bị đảo chỗ
    (t.explanation ?? '').split(/\n\s*\n/).forEach((para) => {
      const lines = para.split('\n').map((l) => l.trim()).filter(Boolean);
      const firstBullet = lines.findIndex((l) => l.startsWith('•'));
      if (firstBullet >= 0 && lines.slice(firstBullet).some((l) => !l.startsWith('•'))) {
        r.warn(at, `dòng thường nằm sau gạch đầu dòng sẽ bị đảo lên trên: "${lines[firstBullet]}"`);
      }
    });
    const contents = new Set();
    (t.questions ?? []).forEach((q, qi) => {
      const aq = `${at} › câu ${qi + 1}`;
      stats.questions++;
      if (isBlank(q.content)) r.error(aq, 'thiếu nội dung câu');
      if (isBlank(q.explanation)) r.error(aq, 'thiếu giải thích');
      if (contents.has(q.content)) r.error(aq, 'trùng câu hỏi trong cùng chủ đề');
      contents.add(q.content);
      if (!Array.isArray(q.options) || q.options.length !== 4) {
        r.error(aq, 'phải có đúng 4 lựa chọn');
      } else {
        if (new Set(q.options.map((o) => o.trim().toLowerCase())).size !== 4) r.error(aq, 'các lựa chọn bị trùng');
        if (q.options.some((o) => isBlank(o) || tooLong(o, LIMITS.option))) r.error(aq, 'lựa chọn trống hoặc quá dài');
      }
      if (!Number.isInteger(q.answer) || q.answer < 0 || q.answer > 3) r.error(aq, '"answer" phải là chỉ số 0–3');
    });
  });
  return stats;
}

/** BlankAnswerRules.check của backend: đáp án được chấp nhận phải đúng định dạng đề yêu cầu. */
function blankFormatProblem(kind, maxWords, raw) {
  const tokens = raw.trim().toLowerCase().split(/\s+/).map((t) => t.replace(/^[^\w%]+|[^\w%]+$/g, '')).filter(Boolean);
  const isNumber = (t) => /^\d+(?:[.,]\d+)*%?$/.test(t);
  const numbers = tokens.filter(isNumber).length;
  const wordCount = tokens.length - numbers;
  if (kind === 'WORDS') return numbers > 0 ? 'không được có số' : wordCount > maxWords ? `quá ${maxWords} từ` : null;
  if (kind === 'NUMBER') return wordCount > 0 || numbers === 0 ? 'phải là đúng một số' : numbers > 1 ? 'quá 1 số' : null;
  return numbers > 1 ? 'quá 1 số' : wordCount > maxWords ? `quá ${maxWords} từ` : null;
}

function checkReading(file, r) {
  const { path, data } = file;
  if (!LEVELS.includes(data.level)) r.error(path, `level "${data.level}" không hợp lệ`);
  const stats = { passages: 0, questions: 0, MCQ: 0, TFNG: 0, BLANK: 0, words: [] };
  (data.passages ?? []).forEach((p, pi) => {
    const at = `${path} › ${p.title ?? `bài #${pi + 1}`}`;
    stats.passages++;
    if (isBlank(p.title) || tooLong(p.title, LIMITS.passageTitle)) r.error(at, 'tiêu đề trống hoặc quá dài');
    if (isBlank(p.content)) r.error(at, 'thiếu nội dung');
    else if (p.content.length > LIMITS.passageContent) r.error(at, `nội dung ${p.content.length} ký tự > ${LIMITS.passageContent}`);
    const content = p.content ?? '';
    stats.words.push(words(content));
    const qs = p.questions ?? [];
    if (qs.length === 0 || qs.length > LIMITS.passageQuestions) r.error(at, `cần 1–${LIMITS.passageQuestions} câu hỏi`);
    qs.forEach((q, qi) => {
      const aq = `${at} › câu ${qi + 1} (${q.type})`;
      stats.questions++;
      stats[q.type] = (stats[q.type] ?? 0) + 1;
      if (isBlank(q.prompt)) r.error(aq, 'thiếu đề');
      if (isBlank(q.explanation)) r.error(aq, 'thiếu giải thích');
      if (isBlank(q.evidence)) r.error(aq, 'thiếu câu bằng chứng');
      else if (!content.includes(q.evidence.trim())) r.error(aq, `bằng chứng không có NGUYÊN VĂN trong bài: "${q.evidence}"`);
      const hints = q.hints ?? [];
      if (hints.length > LIMITS.hints) r.error(aq, `tối đa ${LIMITS.hints} gợi ý`);
      if (hints.length === 0) r.warn(aq, 'chưa có gợi ý đồng nghĩa');
      const seenHints = new Set();
      for (const h of hints) {
        if (isBlank(h.question) || isBlank(h.passage)) { r.error(aq, 'gợi ý thiếu cụm từ'); continue; }
        if (h.question.length > LIMITS.hintPhrase || h.passage.length > LIMITS.hintPhrase) r.error(aq, `cụm gợi ý dài quá ${LIMITS.hintPhrase} ký tự`);
        if (h.question.trim().toLowerCase() === h.passage.trim().toLowerCase()) r.error(aq, `gợi ý "${h.question}" phải là hai cách diễn đạt khác nhau`);
        const key = `${h.question.trim().toLowerCase()}|${h.passage.trim().toLowerCase()}`;
        if (seenHints.has(key)) r.error(aq, 'hai gợi ý trùng nhau');
        seenHints.add(key);
        if (!(q.prompt ?? '').toLowerCase().includes(h.question.toLowerCase())) r.error(aq, `gợi ý "${h.question}" không có trong đề`);
        if (!content.toLowerCase().includes(h.passage.toLowerCase())) r.error(aq, `gợi ý "${h.passage}" không có trong bài`);
      }
      if (q.type === 'MCQ') {
        if (!Array.isArray(q.options) || q.options.length !== 4) r.error(aq, 'phải có đúng 4 lựa chọn');
        else if (new Set(q.options.map((o) => o.trim().toLowerCase())).size !== 4) r.error(aq, 'các lựa chọn bị trùng');
        if (!Number.isInteger(q.answer) || q.answer < 0 || q.answer > 3) r.error(aq, '"answer" phải là chỉ số 0–3');
      } else if (q.type === 'TFNG') {
        if (!TFNG.includes(q.answer)) r.error(aq, `"answer" phải là ${TFNG.join(' / ')}`);
      } else if (q.type === 'BLANK') {
        if ((q.prompt ?? '').split(BLANK).length !== 2) r.error(aq, `đề phải có đúng MỘT chỗ trống ${BLANK}`);
        if (!ANSWER_KINDS.includes(q.answerKind)) r.error(aq, `answerKind phải là ${ANSWER_KINDS.join(' / ')}`);
        if (q.answerKind === 'NUMBER' ? q.maxWords != null : !(q.maxWords >= 1 && q.maxWords <= 3)) {
          r.error(aq, 'maxWords: 1–3 (bỏ trống khi answerKind = NUMBER)');
        }
        const answers = q.answers ?? [];
        if (answers.length === 0 || answers.length > LIMITS.accepted) r.error(aq, `cần 1–${LIMITS.accepted} đáp án`);
        if (new Set(answers.map((a) => a.trim().toLowerCase())).size !== answers.length) r.error(aq, 'hai đáp án trùng nhau');
        for (const a of answers) {
          const problem = ANSWER_KINDS.includes(q.answerKind) ? blankFormatProblem(q.answerKind, q.maxWords, a) : null;
          if (problem) r.error(aq, `đáp án "${a}" ${problem}`);
        }
      } else {
        r.error(aq, 'type phải là MCQ / TFNG / BLANK');
      }
    });
  });
  return stats;
}

function checkVocab(file, r, seenTerms) {
  const { path, data } = file;
  const deck = data.deck ?? {};
  if (isBlank(deck.name) || tooLong(deck.name, LIMITS.deckName)) r.error(path, 'deck.name trống hoặc quá dài');
  if (tooLong(deck.description, LIMITS.deckDescription)) r.error(path, 'deck.description quá dài');
  const stats = { words: 0, senses: 0, fillBlank: 0, sentenceBuild: 0 };
  const terms = new Set();
  (data.words ?? []).forEach((w, wi) => {
    const at = `${path} › ${w.term ?? `từ #${wi + 1}`}`;
    stats.words++;
    if (isBlank(w.term) || tooLong(w.term, LIMITS.term)) r.error(at, 'term trống hoặc quá dài');
    const key = (w.term ?? '').trim().toLowerCase();
    if (terms.has(key)) r.error(at, 'trùng từ trong cùng bộ');
    terms.add(key);
    if (seenTerms.has(key) && seenTerms.get(key) !== path) {
      r.warn(at, `cũng có trong ${seenTerms.get(key)} → dùng chung 1 từ, nghĩa lấy theo file nạp trước`);
    }
    seenTerms.set(key, seenTerms.get(key) ?? path);
    if (tooLong(w.phonetic, LIMITS.phonetic)) r.error(at, 'phiên âm quá dài');
    const senses = w.senses ?? [];
    if (senses.length === 0 || senses.length > LIMITS.senses) r.error(at, `cần 1–${LIMITS.senses} nghĩa`);
    senses.forEach((s, si) => {
      const as = `${at} › nghĩa ${si + 1}`;
      stats.senses++;
      if (!POS.includes(s.pos)) r.error(as, `pos "${s.pos}" không hợp lệ`);
      if (isBlank(s.definition) || tooLong(s.definition, LIMITS.definition)) r.error(as, 'nghĩa trống hoặc quá dài');
      const examples = s.examples ?? [];
      if (examples.length > LIMITS.examples) r.error(as, `tối đa ${LIMITS.examples} câu ví dụ`);
      let marked = false;
      let buildable = false;
      for (const e of examples) {
        if (tooLong(e, LIMITS.example)) r.error(as, 'câu ví dụ quá dài');
        const marks = e.match(/\[[^[\]]+]/g) ?? [];
        const stray = e.replace(/\[[^[\]]+]/, '');
        if (marks.length > 1 || /[[\]]/.test(stray)) r.error(as, `đánh dấu sai (mỗi câu MỘT [từ]): "${e}"`);
        if (marks.length === 1) marked = true;
        const tokens = e.replace(/[[\]]/g, '').trim().split(/\s+/).length;
        if (tokens >= LIMITS.minTokens && tokens <= LIMITS.maxTokens) buildable = true;
      }
      if (marked) stats.fillBlank++; else r.warn(as, 'không có câu đánh dấu [từ] → không dùng được cho Điền từ');
      if (buildable) stats.sentenceBuild++; else r.warn(as, `không có câu ${LIMITS.minTokens}–${LIMITS.maxTokens} từ → không dùng được cho Xếp câu`);
    });
  });
  return stats;
}

// ============================== HTTP ==============================

let token = null;

async function call(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const json = text ? safeJson(text) : null;
  if (!res.ok) {
    const err = new Error(`${method} ${path} → ${res.status} ${json?.code ?? ''} ${json?.message ?? text}`.trim());
    err.status = res.status;
    err.code = json?.code;
    throw err;
  }
  return json;
}

function safeJson(text) {
  try { return JSON.parse(text); } catch { return null; }
}

async function login() {
  const email = args.email ?? process.env.CASSIN_ADMIN_EMAIL;
  const password = args.password ?? process.env.CASSIN_ADMIN_PASSWORD;
  if (!email || !password) {
    throw new Error('Thiếu tài khoản admin: --email/--password hoặc CASSIN_ADMIN_EMAIL/CASSIN_ADMIN_PASSWORD');
  }
  const res = await call('POST', '/api/auth/login', { email, password });
  if (res.user?.role !== 'ADMIN') {
    throw new Error(`${email} không phải tài khoản ADMIN`);
  }
  token = res.accessToken;
}

const norm = (s) => s.trim().replace(/\s+/g, ' ').toLowerCase();

// ============================== nạp ==============================

async function loadGrammar(files, tally) {
  const existing = new Map((await call('GET', '/api/grammar/topics')).map((t) => [norm(t.title), t]));
  for (const { data } of files) {
    for (const t of data.topics) {
      const body = { title: t.title.trim(), level: data.level, explanation: t.explanation.trim() };
      const found = existing.get(norm(t.title));
      let topicId;
      let have = new Set();
      if (found) {
        topicId = found.id;
        await call('PUT', `/api/admin/topics/${topicId}`, body);
        have = new Set((await call('GET', `/api/admin/topics/${topicId}/questions`)).map((q) => norm(q.content)));
        tally.add('grammar', 'topic cập nhật');
      } else {
        topicId = (await call('POST', '/api/admin/topics', body)).id;
        existing.set(norm(t.title), { id: topicId });
        tally.add('grammar', 'topic mới');
      }
      for (const q of t.questions) {
        if (have.has(norm(q.content))) {
          tally.add('grammar', 'câu hỏi bỏ qua (đã có)');
          continue;
        }
        await call('POST', `/api/admin/topics/${topicId}/questions`, {
          content: q.content.trim(),
          explanation: q.explanation.trim(),
          options: q.options.map((o, i) => ({ content: o.trim(), correct: i === q.answer })),
        });
        tally.add('grammar', 'câu hỏi mới');
      }
      console.log(`  ✓ [${data.level}] ${t.title}`);
    }
  }
}

async function allPassageTitles() {
  const titles = new Set();
  for (let page = 0; ; page++) {
    const res = await call('GET', `/api/admin/passages?page=${page}&size=100`);
    res.content.forEach((p) => titles.add(norm(p.title)));
    if (page + 1 >= res.totalPages) {
      return titles;
    }
  }
}

function toQuestionRequest(q) {
  const common = {
    type: q.type,
    prompt: q.prompt.trim(),
    evidence: q.evidence.trim(),
    explanation: q.explanation.trim(),
    hints: (q.hints ?? []).map((h) => ({ questionPhrase: h.question, passagePhrase: h.passage })),
  };
  switch (q.type) {
    case 'MCQ':
      return { ...common, options: q.options.map((o, i) => ({ content: o.trim(), correct: i === q.answer })) };
    case 'TFNG':
      return { ...common, answer: q.answer };
    default:
      return { ...common, answerKind: q.answerKind, maxWords: q.maxWords ?? null, acceptedAnswers: q.answers };
  }
}

async function loadReading(files, tally) {
  const titles = await allPassageTitles();
  for (const { data } of files) {
    for (const p of data.passages) {
      if (titles.has(norm(p.title))) {
        tally.add('reading', 'bài bỏ qua (đã có)');
        console.log(`  · [${data.level}] ${p.title} (đã có)`);
        continue;
      }
      const created = await call('POST', '/api/admin/passages', {
        title: p.title.trim(),
        content: p.content.trim(),
        level: data.level,
        questions: p.questions.map(toQuestionRequest),
      });
      await call('PATCH', `/api/admin/passages/${created.id}/publish`);
      titles.add(norm(p.title));
      tally.add('reading', 'bài mới (đã publish)');
      console.log(`  ✓ [${data.level}] ${p.title}`);
    }
  }
}

async function wordsInDeck(deckId) {
  const terms = new Set();
  for (let page = 0; ; page++) {
    const res = await call('GET', `/api/decks/${deckId}/words?page=${page}&size=100`);
    res.content.forEach((w) => terms.add(norm(w.term)));
    if (page + 1 >= res.totalPages) {
      return terms;
    }
  }
}

async function findSystemWordId(term) {
  const res = await call('GET', `/api/library?q=${encodeURIComponent(term)}&size=100`);
  return res.content.find((w) => norm(w.term) === norm(term))?.id ?? null;
}

async function loadVocab(files, tally) {
  const decks = await call('GET', '/api/decks');
  for (const { data } of files) {
    const name = data.deck.name.trim();
    let deck = decks.find((d) => d.deckType === 'SYSTEM_DECK' && norm(d.deckName) === norm(name));
    if (!deck) {
      deck = await call('POST', '/api/admin/decks', { deckName: name, description: data.deck.description ?? null });
      decks.push(deck);
      tally.add('vocab', 'bộ mới');
    }
    const have = await wordsInDeck(deck.id);
    let added = 0;
    for (const w of data.words) {
      if (have.has(norm(w.term))) {
        tally.add('vocab', 'từ bỏ qua (đã trong bộ)');
        continue;
      }
      const body = {
        term: w.term.trim(),
        phonetic: w.phonetic ?? null,
        senses: w.senses.map((s) => ({ partOfSpeech: s.pos, definition: s.definition.trim(), examples: s.examples ?? [] })),
      };
      try {
        await call('POST', `/api/admin/decks/${deck.id}/words`, body);
        tally.add('vocab', 'từ mới');
      } catch (e) {
        if (e.code !== 'WORD_EXISTS') throw e;
        const wordId = await findSystemWordId(w.term);
        if (wordId === null) throw new Error(`"${w.term}" báo đã tồn tại nhưng không tìm thấy trong Thư viện`);
        await call('POST', `/api/admin/decks/${deck.id}/words/${wordId}`);
        tally.add('vocab', 'từ có sẵn → gắn vào bộ');
        console.log(`    ↪ "${w.term}" đã có trong hệ thống → gắn từ có sẵn (giữ nghĩa cũ, không thêm nghĩa trong file)`);
      }
      have.add(norm(w.term));
      added++;
    }
    console.log(`  ✓ ${name}: +${added} từ`);
  }
}

// ============================== main ==============================

class Tally {
  counts = {};
  add(group, label) {
    const g = (this.counts[group] ??= {});
    g[label] = (g[label] ?? 0) + 1;
  }
}

async function main() {
  console.log(`Dataset: ${DIR}`);
  if (!existsSync(DIR)) {
    throw new Error(`Không thấy thư mục dataset: ${DIR}`);
  }
  const report = new Report();
  const sets = {
    grammar: ONLY.has('grammar') ? readJsonFiles('grammar') : [],
    reading: ONLY.has('reading') ? readJsonFiles('reading') : [],
    vocab: ONLY.has('vocab') ? readJsonFiles('vocab') : [],
  };
  for (const files of Object.values(sets)) {
    files.filter((f) => f.error).forEach((f) => report.error(f.path, f.error));
  }
  const ok = (files) => files.filter((f) => !f.error);

  // ---- 1. kiểm tra toàn bộ trước, chưa gọi API ----
  console.log('\n① Kiểm tra dữ liệu');
  for (const f of ok(sets.grammar)) {
    const s = checkGrammar(f, report);
    console.log(`  grammar/${f.data.level}: ${s.topics} chủ đề, ${s.questions} câu hỏi`);
  }
  for (const f of ok(sets.reading)) {
    const s = checkReading(f, report);
    console.log(`  reading/${f.data.level}: ${s.passages} bài, ${s.questions} câu (MCQ ${s.MCQ} · TFNG ${s.TFNG} · BLANK ${s.BLANK}), độ dài ${s.words.join(' / ')} từ`);
  }
  const seenTerms = new Map();
  for (const f of ok(sets.vocab)) {
    const s = checkVocab(f, report, seenTerms);
    console.log(`  ${f.path}: ${s.words} từ, ${s.senses} nghĩa (Điền từ ${s.fillBlank} · Xếp câu ${s.sentenceBuild})`);
  }
  report.warnings.forEach((w) => console.log(`  ⚠ ${w}`));
  report.errors.forEach((e) => console.log(`  ✗ ${e}`));
  console.log(`  → ${report.errors.length} lỗi, ${report.warnings.length} cảnh báo`);
  if (report.errors.length > 0) {
    throw new Error('Dữ liệu còn lỗi, chưa nạp gì cả. Sửa file rồi chạy lại.');
  }
  if (DRY_RUN) {
    console.log('\n--dry-run: dừng ở bước kiểm tra.');
    return;
  }

  // ---- 2. nạp ----
  console.log(`\n② Nạp vào ${API}`);
  await login();
  const tally = new Tally();
  if (sets.grammar.length) { console.log(' Ngữ pháp'); await loadGrammar(sets.grammar, tally); }
  if (sets.reading.length) { console.log(' Đọc hiểu'); await loadReading(sets.reading, tally); }
  if (sets.vocab.length) { console.log(' Bộ từ'); await loadVocab(sets.vocab, tally); }

  console.log('\n③ Tổng kết');
  for (const [group, counts] of Object.entries(tally.counts)) {
    console.log(`  ${group}: ${Object.entries(counts).map(([k, v]) => `${k} ${v}`).join(' · ')}`);
  }
}

main().catch((e) => {
  console.error(`\n✗ ${e.message}`);
  process.exit(1);
});

import { AbstractControl, FormArray, FormControl, FormGroup, NonNullableFormBuilder, ValidationErrors, Validators } from '@angular/forms';

import { CefrLevel } from '../../../core/models/grammar.models';
import {
  AdminPassage,
  AdminQuestion,
  AnswerKind,
  BLANK_MARKER,
  PASSAGE_LIMITS,
  PassageRequest,
  QuestionRequest,
  QuestionType,
  TfngAnswer,
} from '../../../core/models/passage.models';
import { blankInstruction, checkBlankAnswer } from '../../../core/util/passage-text';

/**
 * [THÊM] Form soạn bài đọc: dựng FormGroup từ AdminPassage và đổi ngược ra PassageRequest.
 * Tách khỏi component để trang soạn bài chỉ lo hiển thị.
 *
 * Mọi dạng câu dùng CHUNG một hình dạng FormGroup (đủ field của cả 3 dạng); field của dạng khác bị bỏ qua
 * khi đổi sang request. Nhờ vậy đổi dạng câu giữa chừng không phải dựng lại form.
 */

export type HintForm = FormGroup<{ questionPhrase: FormControl<string>; passagePhrase: FormControl<string> }>;

export type QuestionForm = FormGroup<{
  type: FormControl<QuestionType>;
  prompt: FormControl<string>;
  evidence: FormControl<string>;
  explanation: FormControl<string>;
  hints: FormArray<HintForm>;
  // MCQ
  options: FormArray<FormControl<string>>;
  correctIndex: FormControl<number | null>;
  // TFNG
  tfngAnswer: FormControl<TfngAnswer | null>;
  // BLANK
  answerKind: FormControl<AnswerKind>;
  maxWords: FormControl<number | null>;
  /** Mỗi dòng một đáp án được chấp nhận */
  acceptedAnswers: FormControl<string>;
}>;

export type PassageForm = FormGroup<{
  title: FormControl<string>;
  level: FormControl<CefrLevel>;
  content: FormControl<string>;
  questions: FormArray<QuestionForm>;
}>;

export function createPassageForm(fb: NonNullableFormBuilder): PassageForm {
  return fb.group({
    title: fb.control('', [Validators.required, Validators.maxLength(PASSAGE_LIMITS.title)]),
    level: fb.control<CefrLevel>('B1'),
    content: fb.control('', [Validators.required, Validators.maxLength(PASSAGE_LIMITS.content)]),
    questions: fb.array<QuestionForm>([]),
  });
}

export function createQuestionForm(fb: NonNullableFormBuilder, type: QuestionType, q?: AdminQuestion): QuestionForm {
  const options = [...(q?.options ?? [])];
  return fb.group(
    {
      type: fb.control<QuestionType>(type),
      prompt: fb.control(q?.prompt ?? '', Validators.required),
      evidence: fb.control(q?.evidence ?? '', Validators.required),
      explanation: fb.control(q?.explanation ?? '', Validators.required),
      hints: fb.array<HintForm>((q?.hints ?? []).map((h) => createHintForm(fb, h.questionPhrase, h.passagePhrase))),
      options: fb.array<FormControl<string>>(
        Array.from({ length: 4 }, (_, i) => fb.control(options[i]?.content ?? '', Validators.maxLength(PASSAGE_LIMITS.option))),
      ),
      correctIndex: new FormControl<number | null>(options.length ? options.findIndex((o) => o.correct) : null),
      tfngAnswer: new FormControl<TfngAnswer | null>(q?.answer ?? null),
      answerKind: fb.control<AnswerKind>(q?.answerKind ?? 'WORDS'),
      maxWords: new FormControl<number | null>(q?.answerKind ? (q.maxWords ?? null) : 1),
      acceptedAnswers: fb.control((q?.acceptedAnswers ?? []).join('\n')),
    },
    { validators: questionRules },
  );
}

export function createHintForm(fb: NonNullableFormBuilder, questionPhrase = '', passagePhrase = ''): HintForm {
  return fb.group({
    questionPhrase: fb.control(questionPhrase, [Validators.required, Validators.maxLength(PASSAGE_LIMITS.phrase)]),
    passagePhrase: fb.control(passagePhrase, [Validators.required, Validators.maxLength(PASSAGE_LIMITS.phrase)]),
  });
}

/** Đổ dữ liệu từ server vào form (mở trang sửa). */
export function fillPassageForm(fb: NonNullableFormBuilder, form: PassageForm, passage: AdminPassage): void {
  form.controls.questions.clear();
  for (const q of passage.questions) {
    form.controls.questions.push(createQuestionForm(fb, q.type, q));
  }
  form.reset({ title: passage.title, level: passage.level, content: passage.content });
}

/** Form → PassageRequest. Chỉ lấy field đúng dạng câu (union có phân biệt theo type). */
export function toPassageRequest(form: PassageForm): PassageRequest {
  const v = form.getRawValue();
  return {
    title: v.title.trim(),
    level: v.level,
    content: v.content.trim(),
    questions: form.controls.questions.controls.map(toQuestionRequest),
  };
}

function toQuestionRequest(group: QuestionForm): QuestionRequest {
  const v = group.getRawValue();
  const base = {
    prompt: v.prompt.trim(),
    evidence: v.evidence.trim(),
    explanation: v.explanation.trim(),
    hints: v.hints.map((h) => ({ questionPhrase: h.questionPhrase.trim(), passagePhrase: h.passagePhrase.trim() })),
  };
  switch (v.type) {
    case 'MCQ':
      return { ...base, type: 'MCQ', options: v.options.map((content, i) => ({ content: content.trim(), correct: i === v.correctIndex })) };
    case 'TFNG':
      return { ...base, type: 'TFNG', answer: v.tfngAnswer! };
    case 'BLANK':
      return {
        ...base,
        type: 'BLANK',
        answerKind: v.answerKind,
        maxWords: v.answerKind === 'NUMBER' ? null : v.maxWords,
        acceptedAnswers: splitLines(v.acceptedAnswers),
      };
  }
}

export function splitLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
}

/**
 * Luật theo từng dạng câu, kiểm tra ngay khi gõ (backend kiểm tra lại y hệt, đây chỉ để admin thấy lỗi sớm).
 * Trả về { rule: "câu báo lỗi" } để template hiện thẳng câu báo lỗi.
 */
function questionRules(control: AbstractControl): ValidationErrors | null {
  const v = (control as QuestionForm).getRawValue();
  const fail = (message: string) => ({ rule: message });
  // Gợi ý: cụm trong câu hỏi phải có trong đề
  for (const hint of v.hints) {
    if (hint.questionPhrase.trim() && v.prompt && !v.prompt.toLowerCase().includes(hint.questionPhrase.trim().toLowerCase())) {
      return fail(`Không tìm thấy "${hint.questionPhrase.trim()}" trong đề`);
    }
  }
  switch (v.type) {
    case 'MCQ': {
      const filled = v.options.map((o) => o.trim());
      if (filled.some((o) => !o)) return fail('Điền đủ 4 lựa chọn');
      if (new Set(filled.map((o) => o.toLowerCase())).size < 4) return fail('Hai lựa chọn không được trùng nhau');
      if (v.correctIndex === null || v.correctIndex < 0) return fail('Bấm “Đúng” ở lựa chọn đúng');
      return null;
    }
    case 'TFNG':
      return v.tfngAnswer ? null : fail('Chọn đáp án True / False / Not Given');
    case 'BLANK': {
      if (v.prompt && v.prompt.split(BLANK_MARKER).length - 1 !== 1) return fail(`Đề phải có đúng một chỗ trống ${BLANK_MARKER}`);
      const answers = splitLines(v.acceptedAnswers);
      if (answers.length === 0) return fail('Nhập ít nhất 1 đáp án được chấp nhận');
      if (answers.length > PASSAGE_LIMITS.acceptedAnswers) return fail('Tối đa 5 đáp án được chấp nhận');
      const maxWords = v.answerKind === 'NUMBER' ? null : v.maxWords;
      const bad = answers.find((a) => checkBlankAnswer(v.answerKind, maxWords, a));
      return bad ? fail(`Đáp án "${bad}" không đúng yêu cầu ${blankInstruction(v.answerKind, maxWords)}`) : null;
    }
  }
}

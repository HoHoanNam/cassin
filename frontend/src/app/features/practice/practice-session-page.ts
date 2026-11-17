import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, map, of, tap } from 'rxjs';

import { DeckApi } from '../../core/api/deck.api';
import { PracticeApi } from '../../core/api/practice.api';
import { errorMessage } from '../../core/models/common.models';
import {
  FillBlankQuestion,
  PRACTICE_LIMITS,
  PracticeAnswer,
  PracticeResult,
  SentenceBuildQuestion,
} from '../../core/models/practice.models';
import { PART_OF_SPEECH } from '../../core/util/part-of-speech';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { PracticeModeParam } from './practice-page';

/** Một câu hỏi, dạng chung cho cả hai chế độ */
type Question =
  | { kind: 'fill'; q: FillBlankQuestion }
  | { kind: 'build'; q: SentenceBuildQuestion };

/** Một mảnh trong bài Xếp câu. id riêng vì câu có thể có 2 mảnh giống nhau ("the" ... "the"). */
interface Piece {
  id: number;
  text: string;
}

interface Answered {
  question: Question;
  given: string;
  result: PracticeResult;
}

/**
 * [THÊM] Chế độ luyện tập trung: /practice/session?mode=fill-blank|sentence-build&deckId=&count=
 *
 * KHÁC trang ôn thẻ: KHÔNG chấm lạc quan. Người học cần biết đúng / sai và đáp án ngay sau mỗi câu,
 * mà chỉ server biết đáp án (câu hỏi không chứa đáp án) → chờ kết quả rồi mới hiện.
 * Phím tắt: Enter = kiểm tra / câu tiếp.
 */
@Component({
  selector: 'app-practice-session-page',
  imports: [RouterLink, EmptyState, ErrorState, Icon],
  templateUrl: './practice-session-page.html',
  styleUrl: './practice-session-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
})
export class PracticeSessionPage {
  readonly mode = input<PracticeModeParam>('fill-blank');
  readonly deckId = input(undefined, { transform: (v: unknown) => (v == null || v === '' ? undefined : numberAttribute(v)) });
  readonly count = input(10, { transform: (v: unknown) => (v == null ? 10 : numberAttribute(v, 10)) });

  private readonly api = inject(PracticeApi);
  private readonly deckApi = inject(DeckApi);
  protected readonly pos = PART_OF_SPEECH;

  /** Lượt hiện tại; tăng lên để tải bộ câu hỏi mới ("Luyện tiếp"). */
  private readonly round = signal(0);

  protected readonly questions = rxResource({
    params: () => ({ mode: this.mode(), deckId: this.deckId() ?? null, count: this.count(), round: this.round() }),
    stream: ({ params }): Observable<Question[]> => {
      const source: Observable<Question[]> =
        params.mode === 'sentence-build'
          ? this.api.sentenceBuild(params.deckId, params.count).pipe(map((list) => list.map((q) => ({ kind: 'build' as const, q }))))
          : this.api.fillBlank(params.deckId, params.count).pipe(map((list) => list.map((q) => ({ kind: 'fill' as const, q }))));
      return source.pipe(tap((list) => this.startQuestion(list[0])));
    },
  });

  protected readonly deck = rxResource({
    params: () => this.deckId(),
    stream: ({ params }) => (params !== undefined ? this.deckApi.getDeck(params) : of(null)),
  });

  protected readonly index = signal(0);
  protected readonly answered = signal<Answered[]>([]);
  /** Kết quả câu hiện tại (null = chưa nộp) */
  protected readonly result = signal<PracticeResult | null>(null);
  protected readonly checking = signal(false);
  protected readonly error = signal<string | null>(null);

  // ---- Điền từ ----
  protected readonly typed = signal('');
  // ---- Xếp câu ----
  protected readonly pool = signal<Piece[]>([]);
  protected readonly picked = signal<Piece[]>([]);

  private shownAt = performance.now();

  protected readonly list = computed(() => (this.questions.hasValue() ? this.questions.value() : []));
  protected readonly current = computed(() => this.list()[this.index()] ?? null);
  protected readonly finished = computed(() => this.list().length > 0 && this.index() >= this.list().length);
  protected readonly progress = computed(() =>
    this.list().length ? Math.round(((this.index() + (this.result() ? 1 : 0)) / this.list().length) * 100) : 0,
  );
  protected readonly correctCount = computed(() => this.answered().filter((a) => a.result.correct).length);
  protected readonly wrong = computed(() => this.answered().filter((a) => !a.result.correct));
  protected readonly modeTitle = computed(() => (this.mode() === 'sentence-build' ? 'Xếp câu' : 'Điền từ'));
  protected readonly scopeTitle = computed(() => (this.deck.hasValue() && this.deck.value()?.deckName) || 'Cả sổ từ');

  /** Gợi ý ô điền: "i _ _ _ _ _ _ _" */
  protected readonly blankHint = computed(() => {
    const item = this.current();
    if (item?.kind !== 'fill') {
      return '';
    }
    return [item.q.firstLetter, ...Array(Math.max(item.q.answerLength - 1, 0)).fill('_')].join(' ');
  });
  protected readonly canCheck = computed(() => {
    const item = this.current();
    if (!item || this.result() || this.checking()) {
      return false;
    }
    return item.kind === 'fill' ? this.typed().trim().length > 0 : this.pool().length === 0 && this.picked().length > 0;
  });

  protected onTyped(event: Event): void {
    this.typed.set((event.target as HTMLInputElement).value);
  }

  protected pick(piece: Piece): void {
    if (this.result()) {
      return;
    }
    this.pool.update((list) => list.filter((p) => p.id !== piece.id));
    this.picked.update((list) => [...list, piece]);
  }

  protected unpick(piece: Piece): void {
    if (this.result()) {
      return;
    }
    this.picked.update((list) => list.filter((p) => p.id !== piece.id));
    this.pool.update((list) => [...list, piece]);
  }

  protected clearPicked(): void {
    this.pool.update((list) => [...list, ...this.picked()]);
    this.picked.set([]);
  }

  protected check(): void {
    const item = this.current();
    if (!item || !this.canCheck()) {
      return;
    }
    const given = item.kind === 'fill' ? this.typed().trim() : this.picked().map((p) => p.text).join(' ');
    const body: PracticeAnswer = {
      senseId: item.q.senseId,
      exampleIndex: item.q.exampleIndex,
      mode: item.kind === 'fill' ? 'FILL_BLANK' : 'SENTENCE_BUILD',
      answer: given,
      durationMs: Math.min(Math.round(performance.now() - this.shownAt), PRACTICE_LIMITS.maxDurationMs),
    };
    this.checking.set(true);
    this.error.set(null);
    this.api.answer(body).subscribe({
      next: (result) => {
        this.checking.set(false);
        this.result.set(result);
        this.answered.update((list) => [...list, { question: item, given, result }]);
      },
      error: (error: unknown) => {
        this.checking.set(false);
        this.error.set(errorMessage(error, 'Chưa chấm được câu này. Thử lại nhé.'));
      },
    });
  }

  protected next(): void {
    this.index.update((i) => i + 1);
    this.startQuestion(this.list()[this.index()]);
  }

  /** Luyện tiếp: tải bộ câu hỏi mới cùng chế độ, cùng phạm vi. */
  protected again(): void {
    this.index.set(0);
    this.answered.set([]);
    this.round.update((r) => r + 1);
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.isComposing) {
      return;
    }
    if (this.result()) {
      event.preventDefault();
      this.next();
    } else if (this.canCheck()) {
      event.preventDefault();
      this.check();
    }
  }

  private startQuestion(item: Question | undefined): void {
    this.result.set(null);
    this.error.set(null);
    this.typed.set('');
    this.picked.set([]);
    this.pool.set(item?.kind === 'build' ? item.q.tokens.map((text, id) => ({ id, text })) : []);
    this.shownAt = performance.now();
  }
}

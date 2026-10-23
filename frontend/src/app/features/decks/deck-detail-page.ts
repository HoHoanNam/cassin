import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { BoxMeter } from '../../shared/box-meter/box-meter';
import { PosLabelPipe } from '../../shared/pos-label.pipe';
import { WordEntry } from './deck.models';
import { DeckService } from './deck.service';

@Component({
  selector: 'app-deck-detail-page',
  imports: [RouterLink, BoxMeter, PosLabelPipe],
  templateUrl: './deck-detail-page.html',
  styleUrl: './deck-detail-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeckDetailPage {
  private readonly deckService = inject(DeckService);

  /** :id trên URL /decks/:id */
  readonly id = input.required<string>();

  protected readonly deck = rxResource({
    params: () => ({ id: Number(this.id()) }),
    stream: ({ params }) => this.deckService.getDeck(params.id),
  });

  /** Từ khóa trong ô tìm kiếm. */
  protected readonly query = signal('');

  /**
   * Gom các thẻ cùng một từ thành một mục (light: ánh sáng / nhẹ / thắp),
   * đồng thời lọc theo ô tìm kiếm.
   *
   * Dùng Map với key là từ viết thường: gặp lại từ đã có thì thêm nghĩa vào mục cũ,
   * chưa có thì tạo mục mới. Map giữ đúng thứ tự thêm vào, nên mục từ hiện theo thứ tự thẻ.
   */
  protected readonly entries = computed<WordEntry[]>(() => {
    const cards = this.deck.value()?.cards ?? [];
    const keyword = this.query().trim().toLowerCase();
    const byWord = new Map<string, WordEntry>();

    for (const card of cards) {
      const matches =
        !keyword ||
        card.word.toLowerCase().includes(keyword) ||
        card.definition.toLowerCase().includes(keyword);
      if (!matches) {
        continue;
      }
      const key = card.word.toLowerCase();
      const entry = byWord.get(key);
      if (entry) {
        entry.senses.push(card);
      } else {
        byWord.set(key, { word: card.word, phonetic: card.phonetic, senses: [card] });
      }
    }
    return [...byWord.values()];
  });

  protected readonly wordCount = computed(
    () => new Set(this.deck.value()?.cards.map((c) => c.word.toLowerCase())).size,
  );

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
}

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { notebookDate } from '../../shared/vn-date';
import { DeckList } from './deck-list/deck-list';
import { GrammarList } from './grammar-list/grammar-list';
import { HomeService } from './home.service';
import { QuizHistory } from './quiz-history/quiz-history';
import { ReviewPanel } from './review-panel/review-panel';

/**
 * Trang chủ. Chỉ lo lấy dữ liệu và xếp bố cục;
 * phần hiển thị chi tiết nằm trong 4 component con.
 */
@Component({
  selector: 'app-home-page',
  imports: [ReviewPanel, DeckList, GrammarList, QuizHistory],
  templateUrl: './home-page.html',
  styleUrl: './home-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  private readonly homeService = inject(HomeService);

  // toSignal: chuyển Observable thành signal. Trước khi có dữ liệu, giá trị là undefined.
  protected readonly data = toSignal(this.homeService.getHome());

  protected readonly today = notebookDate(new Date());
}

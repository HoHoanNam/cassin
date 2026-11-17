import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { PracticeApi } from '../../core/api/practice.api';
import { PRACTICE_LIMITS } from '../../core/models/practice.models';
import { relativeDay } from '../../core/util/dates';
import { PART_OF_SPEECH } from '../../core/util/part-of-speech';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/**
 * [THÊM] Sổ lỗi sai (GET /api/mistakes?days=&limit=): những NGHĨA đang sai gần đây, sai nhiều nhất trước.
 * "Đang sai" = lần trả lời gần nhất vẫn sai. Làm đúng lại một lần là nghĩa đó rời khỏi sổ.
 * Tính cả lật thẻ (Quên), Điền từ và Xếp câu.
 */
@Component({
  selector: 'app-mistakes-page',
  imports: [RouterLink, EmptyState, ErrorState, Skeleton],
  templateUrl: './mistakes-page.html',
  styleUrl: './mistakes-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MistakesPage {
  private readonly api = inject(PracticeApi);

  protected readonly ranges = [
    { days: 7, label: '7 ngày' },
    { days: 30, label: '30 ngày' },
  ];
  protected readonly days = signal(7);
  protected readonly pos = PART_OF_SPEECH;
  protected readonly relativeDay = relativeDay;

  protected readonly mistakes = rxResource({
    params: () => this.days(),
    stream: ({ params }) => this.api.mistakes(params, PRACTICE_LIMITS.mistakeMaxLimit),
  });
}

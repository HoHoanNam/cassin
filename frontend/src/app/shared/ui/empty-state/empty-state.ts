import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Icon, IconName } from '../icon/icon';

/**
 * Trạng thái trống: nói rõ tình trạng và chỉ ĐÚNG MỘT việc nên làm tiếp.
 * Nút hành động truyền vào qua <ng-content>.
 */
@Component({
  selector: 'app-empty-state',
  imports: [Icon],
  templateUrl: './empty-state.html',
  styleUrl: './empty-state.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyState {
  readonly icon = input<IconName>('decks');
  readonly title = input.required<string>();
  readonly description = input<string>('');
}

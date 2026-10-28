import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { CefrLevel } from '../../../core/models/grammar.models';

/** Ô vuông ghi cấp độ CEFR (A1..C2), kiểu chữ viết tay. */
@Component({
  selector: 'app-level-badge',
  templateUrl: './level-badge.html',
  styleUrl: './level-badge.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LevelBadge {
  readonly level = input.required<CefrLevel>();
}

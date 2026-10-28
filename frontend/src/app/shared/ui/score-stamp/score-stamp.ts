import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Điểm khoanh bút đỏ như cô giáo chấm bài: "8/10". */
@Component({
  selector: 'app-score-stamp',
  templateUrl: './score-stamp.html',
  styleUrl: './score-stamp.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScoreStamp {
  readonly score = input.required<number>();
  readonly total = input.required<number>();
  readonly size = input<'sm' | 'lg'>('sm');
}

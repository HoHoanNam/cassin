import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { GrammarTopicSummary } from '../home.models';

@Component({
  selector: 'app-grammar-list',
  imports: [RouterLink],
  templateUrl: './grammar-list.html',
  styleUrl: './grammar-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GrammarList {
  readonly topics = input.required<GrammarTopicSummary[]>();
}

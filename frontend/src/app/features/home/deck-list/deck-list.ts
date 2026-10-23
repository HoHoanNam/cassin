import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DeckSummary } from '../home.models';

@Component({
  selector: 'app-deck-list',
  imports: [RouterLink],
  templateUrl: './deck-list.html',
  styleUrl: './deck-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeckList {
  readonly decks = input.required<DeckSummary[]>();
}

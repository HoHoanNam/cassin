import { EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';

import { MockAuthApi } from '../mock/mock-auth.api';
import { MockDeckApi } from '../mock/mock-deck.api';
import { MockGrammarApi } from '../mock/mock-grammar.api';
import { MockQuizApi } from '../mock/mock-quiz.api';
import { MockReviewApi } from '../mock/mock-review.api';
import { AuthApi } from './auth.api';
import { DeckApi } from './deck.api';
import { GrammarApi } from './grammar.api';
import { HttpAuthApi } from './http/http-auth.api';
import { HttpDeckApi } from './http/http-deck.api';
import { HttpGrammarApi } from './http/http-grammar.api';
import { HttpQuizApi } from './http/http-quiz.api';
import { HttpReviewApi } from './http/http-review.api';
import { QuizApi } from './quiz.api';
import { ReviewApi } from './review.api';

/**
 * Chọn bản cài đặt cho từng "hợp đồng" API.
 * { provide: DeckApi, useClass: MockDeckApi } nghĩa là: ai inject(DeckApi) sẽ nhận một MockDeckApi.
 */
export function provideApi(useMock: boolean): EnvironmentProviders {
  const providers: Provider[] = useMock
    ? [
        { provide: AuthApi, useClass: MockAuthApi },
        { provide: DeckApi, useClass: MockDeckApi },
        { provide: ReviewApi, useClass: MockReviewApi },
        { provide: GrammarApi, useClass: MockGrammarApi },
        { provide: QuizApi, useClass: MockQuizApi },
      ]
    : [
        { provide: AuthApi, useClass: HttpAuthApi },
        { provide: DeckApi, useClass: HttpDeckApi },
        { provide: ReviewApi, useClass: HttpReviewApi },
        { provide: GrammarApi, useClass: HttpGrammarApi },
        { provide: QuizApi, useClass: HttpQuizApi },
      ];
  return makeEnvironmentProviders(providers);
}

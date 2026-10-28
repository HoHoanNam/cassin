import { Observable } from 'rxjs';

import { CefrLevel, TopicDetail, TopicSummary } from '../models/grammar.models';

export abstract class GrammarApi {
  /** GET /api/grammar/topics?level= (level null = tất cả) */
  abstract listTopics(level: CefrLevel | null): Observable<TopicSummary[]>;
  /** GET /api/grammar/topics/{topicId} */
  abstract getTopic(topicId: number): Observable<TopicDetail>;
}

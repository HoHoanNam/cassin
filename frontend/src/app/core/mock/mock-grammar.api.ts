import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { GrammarApi } from '../api/grammar.api';
import { CEFR_LEVELS, CefrLevel, TopicDetail, TopicSummary } from '../models/grammar.models';
import { DbTopic, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { notFound, simulate } from './mock-utils';

@Injectable()
export class MockGrammarApi extends GrammarApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  listTopics(level: CefrLevel | null): Observable<TopicSummary[]> {
    return simulate(() => {
      this.session.userId();
      return this.db.topics
        .filter((t) => level === null || t.level === level)
        .sort((a, b) => CEFR_LEVELS.indexOf(a.level) - CEFR_LEVELS.indexOf(b.level) || a.title.localeCompare(b.title))
        .map((t) => this.toSummary(t));
    });
  }

  getTopic(topicId: number): Observable<TopicDetail> {
    return simulate(() => {
      this.session.userId();
      const topic = this.db.topics.find((t) => t.id === topicId);
      if (!topic) {
        throw notFound('TOPIC_NOT_FOUND', 'Không tìm thấy chủ đề');
      }
      return { ...this.toSummary(topic), explanation: topic.explanation };
    });
  }

  private toSummary(topic: DbTopic): TopicSummary {
    return {
      id: topic.id,
      title: topic.title,
      level: topic.level,
      questionCount: this.db.questions.filter((q) => q.topicId === topic.id).length,
    };
  }
}

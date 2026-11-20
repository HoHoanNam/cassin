import { Injectable } from '@angular/core';

import { Session } from '../models/session.models';

const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;

/**
 * [THÊM] Thiết bị đang đăng nhập của bản mock, giữ trong bộ nhớ theo userId (tải lại trang là có lại).
 * Dùng chung cho MockSessionApi và MockAccountApi (đổi mật khẩu → đăng xuất mọi thiết bị khác).
 */
@Injectable({ providedIn: 'root' })
export class MockDevices {
  private readonly byUser = new Map<number, Session[]>();

  of(userId: number): Session[] {
    let sessions = this.byUser.get(userId);
    if (!sessions) {
      sessions = seed();
      this.byUser.set(userId, sessions);
    }
    return sessions;
  }

  /** Giữ lại phiên thỏa điều kiện, trả số phiên đã bỏ. */
  keep(userId: number, predicate: (session: Session) => boolean): number {
    const before = this.of(userId);
    const after = before.filter(predicate);
    this.byUser.set(userId, after);
    return before.length - after.length;
  }
}

function seed(): Session[] {
  const now = Date.now();
  const iso = (msAgo: number) => new Date(now - msAgo).toISOString();
  const current = parseUserAgent(navigator.userAgent);
  return [
    { id: 'mock-this-device', ...current, signedInAt: iso(2 * DAY_MS), lastActiveAt: iso(0), current: true },
    { id: 'mock-iphone', browser: 'Safari', os: 'iOS', signedInAt: iso(9 * DAY_MS), lastActiveAt: iso(2 * HOUR_MS), current: false },
    { id: 'mock-office-pc', browser: 'Edge', os: 'Windows', signedInAt: iso(12 * DAY_MS), lastActiveAt: iso(5 * DAY_MS), current: false },
  ];
}

/** Bản rút gọn của DeviceLabel ở backend, cùng thứ tự luật (Edge/Opera trước Chrome, Chrome trước Safari...). */
function parseUserAgent(ua: string): Pick<Session, 'browser' | 'os'> {
  const browsers: [string, string][] = [
    ['Edg', 'Edge'], ['OPR/', 'Opera'], ['CriOS/', 'Chrome'], ['FxiOS/', 'Firefox'],
    ['Firefox/', 'Firefox'], ['Chrome/', 'Chrome'], ['Safari/', 'Safari'],
  ];
  const systems: [string, string][] = [
    ['Windows', 'Windows'], ['Android', 'Android'], ['iPhone', 'iOS'], ['iPad', 'iPadOS'],
    ['Mac OS X', 'macOS'], ['CrOS', 'ChromeOS'], ['Linux', 'Linux'],
  ];
  const find = (rules: [string, string][]) => rules.find(([key]) => ua.includes(key))?.[1] ?? 'Không rõ';
  return { browser: find(browsers), os: find(systems) };
}

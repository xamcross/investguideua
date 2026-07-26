import { timeAgo } from './news-time.util';

describe('timeAgo', () => {
  const now = new Date('2026-07-26T12:00:00Z');

  it('buckets into justNow / minutes / hours / days with translate params', () => {
    expect(timeAgo('2026-07-26T11:59:40Z', now)).toEqual({ key: 'news.timeAgo.justNow' });
    expect(timeAgo('2026-07-26T11:15:00Z', now)).toEqual({
      key: 'news.timeAgo.minutes',
      params: { n: 45 },
    });
    expect(timeAgo('2026-07-26T09:00:00Z', now)).toEqual({
      key: 'news.timeAgo.hours',
      params: { n: 3 },
    });
    expect(timeAgo('2026-07-23T12:00:00Z', now)).toEqual({
      key: 'news.timeAgo.days',
      params: { n: 3 },
    });
  });

  it('treats unparseable and future dates as justNow', () => {
    expect(timeAgo('not-a-date', now)).toEqual({ key: 'news.timeAgo.justNow' });
    expect(timeAgo('2026-07-26T12:30:00Z', now)).toEqual({ key: 'news.timeAgo.justNow' });
  });
});

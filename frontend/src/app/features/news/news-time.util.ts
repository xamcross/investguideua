/** Translate-key + params pair: the component pipes it through ngx-translate (feature 016). */
export interface TimeAgo {
  key: string;
  params?: { n: number };
}

/**
 * Coarse relative age for a headline. Language-agnostic on purpose: it returns i18n keys
 * (news.timeAgo.*), so switching the UI language re-renders it correctly for free.
 * Unparseable or future dates read as "just now" - a headline must never show a negative age.
 */
export function timeAgo(publishedAt: string, now: Date): TimeAgo {
  const t = Date.parse(publishedAt);
  if (!Number.isFinite(t)) {
    return { key: 'news.timeAgo.justNow' };
  }
  const minutes = Math.floor((now.getTime() - t) / 60_000);
  if (minutes < 1) {
    return { key: 'news.timeAgo.justNow' };
  }
  if (minutes < 60) {
    return { key: 'news.timeAgo.minutes', params: { n: minutes } };
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return { key: 'news.timeAgo.hours', params: { n: hours } };
  }
  return { key: 'news.timeAgo.days', params: { n: Math.floor(hours / 24) } };
}

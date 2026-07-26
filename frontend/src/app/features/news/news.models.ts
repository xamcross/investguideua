/** Mirrors backend NewsItemResponse / NewsLatestResponse (feature 016, FR-4). */
export interface NewsItem {
  sourceName: string;
  title: string;
  link: string;
  publishedAt: string; // ISO instant
}

export interface NewsLatest {
  ukraine: NewsItem[];
  world: NewsItem[];
}

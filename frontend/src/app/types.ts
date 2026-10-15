export type ReaderPreferences = {
  fontFamily: "serif" | "sans" | "dyslexic";
  fontSize: number;
  lineHeight: number;
  foreground: string;
  background: string;
};

export type ReaderChapter = {
  id: string;
  href: string;
  title: string;
  depth?: number;
};

export type ReadiumBridge = {
  mount: (
    bookId: string,
    container: HTMLElement,
    onLocatorChanged?: (progression: number) => void,
  ) => Promise<void>;
  unmount: () => Promise<void>;
  goToChapter: (chapterId: string) => Promise<boolean>;
  chapters: () => Promise<ReaderChapter[]>;
  next: () => Promise<boolean>;
  previous: () => Promise<boolean>;
  applyPreferences: (preferences: ReaderPreferences) => void;
};

export type CatalogBook = {
  id: string;
  canonicalTitle: string;
  originalLanguage?: string | null;
  description?: string | null;
  thumbnailUrl?: string | null;
};

export type FeedExcerpt = {
  id: string;
  text: string;
  sourceAssetVersionId: string;
  textSha256: string;
  startCodepoint: number;
  endCodepoint: number;
  generationMethod: string;
  generatorVersion: string;
  rank?: { version?: string; score?: number | null } | null;
};

export type PageLoopBook = {
  id: string;
  title: string;
  author: string;
  genre: string;
  keywords: string[];
  match: number;
  a: string;
  b: string;
  quote: string;
  excerpt?: FeedExcerpt | null;
  likes: number;
  comments: number;
  shares: number;
  pages: number;
  progress: number;
  thumbnailUrl?: string | null;
  recommendationRequestId?: string;
  impressionId?: string;
  modelVersion?: string;
  rank?: number;
};

export type RecommendationItem = {
  book?: CatalogBook;
  impressionId?: string;
  recommendationRequestId?: string;
  modelVersion?: string;
  rank?: number;
  excerpt?: FeedExcerpt | null;
};

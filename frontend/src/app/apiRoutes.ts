const id = (value: string) => encodeURIComponent(value);

/** Central registry for frontend API paths. Keep service ownership visible here. */
export const apiRoutes = {
  catalog: {
    list: (query = "page=0&size=50") => `/api/v1/books?${query}`,
    book: (bookId: string) => `/api/v1/books/${id(bookId)}`,
    search: (query: string) => `/api/v1/books?${query}`,
  },
  reader: {
    feed: (size = 50) => `/api/v1/reader/feed?size=${size}`,
    recommendations: () => "/api/v1/reader/recommendations",
    library: () => "/api/v1/reader/library",
    libraryBook: (bookId: string) => `/api/v1/reader/library/${id(bookId)}`,
    recent: () => "/api/v1/reader/recent",
    streak: () => "/api/v1/reader/streak",
    plant: () => "/api/v1/reader/plant",
    plantReadingTime: () => "/api/v1/reader/plant",
    book: (bookId: string) => `/api/v1/reader/books/${id(bookId)}`,
    progress: (bookId: string) => `${apiRoutes.reader.book(bookId)}/progress`,
    bookmarks: (bookId: string) => `${apiRoutes.reader.book(bookId)}/bookmarks`,
    opened: (bookId: string) => `${apiRoutes.reader.book(bookId)}/opened`,
    chapters: (bookId: string) => `${apiRoutes.reader.book(bookId)}/chapters`,
    publication: (bookId: string) =>
      `${apiRoutes.reader.book(bookId)}/publication.json`,
    chapterContent: (bookId: string, chapterId: string) =>
      `${apiRoutes.reader.book(bookId)}/chapters/${id(chapterId)}/content`,
    resource: (bookId: string, resourcePath: string) =>
      `${apiRoutes.reader.book(bookId)}/resources/${resourcePath
        .split("/")
        .map(id)
        .join("/")}`,
    impressionViewable: (impressionId: string) =>
      `/api/v1/reader/recommendations/impressions/${id(impressionId)}/viewable`,
  },
  content: {
    book: (bookId: string) => `/api/v1/content/books/${id(bookId)}`,
  },
  social: {
    likes: (bookId: string) => `/api/v1/social/books/${id(bookId)}/likes`,
    comments: (bookId: string) => `/api/v1/social/books/${id(bookId)}/comments`,
    comment: (commentId: string) => `/api/v1/social/comments/${id(commentId)}`,
    like: (bookId: string) => `/api/v1/social/books/${id(bookId)}/like`,
    share: (bookId: string) => `/api/v1/social/books/${id(bookId)}/share`,
    followers: (subject?: string) =>
      `/api/v1/social/followers${subject ? `?user=${id(subject)}` : ""}`,
    follow: (subject: string) => `/api/v1/social/follow?user=${id(subject)}`,
    reports: () => "/api/v1/social/reports",
  },
  behavior: () => "/api/v1/behavior/events",
  profile: {
    self: () => "/api/v1/profile",
    privacy: () => "/api/v1/profile/privacy",
    public: (subject: string) =>
      `/api/v1/profile/public?subject=${id(subject)}`,
  },
  admin: {
    users: () => "/api/v1/admin/users",
    reports: () => "/api/v1/admin/reports",
    audit: () => "/api/v1/admin/audit",
    moderation: () => "/api/v1/admin/moderation",
  },
  publisher: {
    submissions: () => "/api/v1/publisher/submissions",
    submission: (submissionId: string) =>
      `/api/v1/publisher/submissions/${id(submissionId)}`,
    submit: (submissionId: string) =>
      `${apiRoutes.publisher.submission(submissionId)}/submit`,
    metrics: (submissionId: string) =>
      `${apiRoutes.publisher.submission(submissionId)}/metrics`,
    upload: (submissionId: string) =>
      `${apiRoutes.publisher.submission(submissionId)}/upload`,
    finalize: (submissionId: string, uploadId: string) =>
      `${apiRoutes.publisher.upload(submissionId)}/${id(uploadId)}/finalize`,
  },
} as const;

export function readerApiPath(path: string): string {
  return path.replace(/^\/api\/v1\/content\/books\//, "/api/v1/reader/books/");
}

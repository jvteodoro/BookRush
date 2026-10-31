import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppShell, type ReaderTab } from "../components/layout/AppShell";
import { ExcerptFeed } from "../components/book/ExcerptFeed";
import { Card } from "../components/ui/Card";
import { authClient } from "../auth";
import { apiRoutes } from "../app/apiRoutes";
import { LibraryPage } from "./LibraryPage";
import type { LibraryBook } from "./library";
import { SearchPage } from "./SearchPage";
import { RecommendationsPage } from "./RecommendationsPage";
import { CommentsDrawer } from "./CommentsDrawer";
import { AccountPage } from "./AccountPages";
import type { FeedExcerpt } from "../app/types";

type SourceBook = {
  id: string;
  title: string;
  author: string;
  genre: string;
  quote: string;
  progress: number;
  likes?: number;
  comments?: number;
  thumbnailUrl?: string | null;
  excerpt?: FeedExcerpt | null;
  impressionId?: string;
  recommendationItemId?: string;
};
type SocialCounts = { likes: number; comments: number; liked: boolean };
type Runtime = {
  __BOOKRUSH_REACT_COMMENTS__?: boolean;
  __BOOKRUSH_BOOKS__: SourceBook[];
  __BOOKRUSH_LIKES__: Record<string, boolean>;
  __BOOKRUSH_STREAK__?: { currentDays?: number };
  __BOOKRUSH_PLANT__?: {
    formulaVersion?: string;
    stage?: number;
    streakDays?: number;
    totalReadingMinutes?: number;
    measuredUntil?: string;
    sad?: boolean;
    todaySeconds?: number;
    goalSeconds?: number;
    goalReached?: boolean;
  } | null;
  __BOOKRUSH_API__: {
    saved: Set<string>;
    save: (id: string, enabled: boolean) => Promise<void>;
    like: (id: string, enabled: boolean) => Promise<void>;
    refreshCounts: (id: string) => Promise<SocialCounts>;
    viewable: (id: string) => Promise<void>;
    trackExcerpt: (
      type: string,
      id: string,
      excerpt: unknown,
      payload?: Record<string, unknown>,
    ) => void;
  };
  __BOOKRUSH_LEGACY__: {
    navigate: (route: string, query?: string) => void;
    openBook: (id: string, query?: string) => Promise<void>;
    openComments: (id: string) => Promise<void>;
    openShare: (id: string) => void;
    openLucky: () => void;
    openProfile: (subject: string) => void;
  };
};
const runtime = () => window as unknown as Runtime;

export function LiveReader({
  visible,
  userName,
  onLegacy,
  initialTab = "feed",
  legacyQuery = "",
}: {
  visible: boolean;
  userName: string;
  onLegacy: () => void;
  initialTab?: "feed" | "library" | "search" | "recommendations";
  legacyQuery?: string;
}) {
  const [tab, setTab] = useState<ReaderTab>("feed");
  const [plant, setPlant] = useState(runtime().__BOOKRUSH_PLANT__);
  const [plantLoading, setPlantLoading] = useState(false);
  const [plantError, setPlantError] = useState(false);
  useEffect(() => {
    if (tab !== "plant") return;
    let cancelled = false;
    const refresh = async () => {
      setPlantLoading(true);
      try {
        const response = await authClient.fetch(apiRoutes.reader.plant());
        if (!response.ok) throw new Error("plant unavailable");
        const value = await response.json();
        if (!cancelled) {
          runtime().__BOOKRUSH_PLANT__ = value;
          setPlant(value);
          setPlantError(false);
        }
      } catch {
        if (!cancelled) setPlantError(true);
      } finally {
        if (!cancelled) setPlantLoading(false);
      }
    };
    void refresh();
    window.addEventListener("bookrush:plant-updated", refresh);
    return () => {
      cancelled = true;
      window.removeEventListener("bookrush:plant-updated", refresh);
    };
  }, [tab]);
  const [query, setQuery] = useState("");
  const [saved, setSaved] = useState(() => [
    ...runtime().__BOOKRUSH_API__.saved,
  ]);
  const [liked, setLiked] = useState(() =>
    Object.keys(runtime().__BOOKRUSH_LIKES__).filter(
      (id) => runtime().__BOOKRUSH_LIKES__[id],
    ),
  );
  const [pending, setPending] = useState<string[]>([]);
  const [commentsBook, setCommentsBook] = useState<{
    id: string;
    title: string;
  } | null>(null);
  useEffect(() => {
    const open = (event: Event) =>
      setCommentsBook(
        (event as CustomEvent<{ id: string; title: string }>).detail,
      );
    runtime().__BOOKRUSH_REACT_COMMENTS__ = true;
    window.addEventListener("bookrush:open-comments", open);
    return () => {
      runtime().__BOOKRUSH_REACT_COMMENTS__ = false;
      window.removeEventListener("bookrush:open-comments", open);
    };
  }, []);
  const onCommentCount = useCallback((id: string, total: number) => {
    setCounts((current) => ({
      ...current,
      [id]: { likes: current[id]?.likes ?? 0, comments: total },
    }));
    const book = runtime().__BOOKRUSH_BOOKS__.find((item) => item.id === id);
    if (book) book.comments = total;
  }, []);
  const [counts, setCounts] = useState<
    Record<string, { likes: number; comments: number }>
  >(() =>
    Object.fromEntries(
      runtime().__BOOKRUSH_BOOKS__.map((book) => [
        book.id,
        { likes: book.likes ?? 0, comments: book.comments ?? 0 },
      ]),
    ),
  );
  useEffect(() => {
    const update = (event: Event) => {
      const { id, counts } = (
        event as CustomEvent<{ id: string; counts: SocialCounts }>
      ).detail;
      setCounts((current) => ({ ...current, [id]: counts }));
      setLiked((current) =>
        counts.liked
          ? [...new Set([...current, id])]
          : current.filter((item) => item !== id),
      );
    };
    window.addEventListener("bookrush:social-counts", update);
    return () => window.removeEventListener("bookrush:social-counts", update);
  }, []);
  const [message, setMessage] = useState("");
  const locks = useRef(new Set<string>());
  const viewed = useRef(new Set<string>());
  const saveRevision = useRef(0);
  const syncSaved = useCallback((ids: string[]) => {
    runtime().__BOOKRUSH_API__.saved.clear();
    ids.forEach((id) => runtime().__BOOKRUSH_API__.saved.add(id));
    setSaved(ids);
  }, []);
  const books = useMemo(
    () =>
      runtime().__BOOKRUSH_BOOKS__.map((book) => ({
        id: book.id,
        title: book.title,
        author: book.author,
        genre: book.genre,
        progress: book.progress,
        minutes: 0,
        description: book.excerpt?.text ?? "",
        excerpt: book.excerpt ?? null,
        coverUrl: book.thumbnailUrl ?? undefined,
      })),
    [],
  );
  useEffect(() => {
    if (initialTab === "search") setQuery(legacyQuery);
  }, [initialTab, legacyQuery]);
  useEffect(() => {
    if (!visible) return;
    setTab(initialTab);
    setLiked(
      Object.keys(runtime().__BOOKRUSH_LIKES__).filter(
        (id) => runtime().__BOOKRUSH_LIKES__[id],
      ),
    );
  }, [visible, initialTab]);
  useEffect(() => {
    // LibraryPage owns the full snapshot while visible. Cancel the feed's
    // background read when switching tabs so it cannot overwrite that snapshot.
    if (!visible || tab === "library") return;
    let active = true;
    const revision = saveRevision.current;
    void authClient
      .fetch(apiRoutes.reader.library())
      .then(async (response) => {
        if (!response.ok)
          throw new Error("Não foi possível atualizar sua biblioteca.");
        const rows = (await response.json()) as Array<{
          book_id?: string;
          bookId?: string;
        }>;
        if (active && saveRevision.current === revision) {
          const ids = rows
            .map((row) => row.book_id ?? row.bookId)
            .filter((id): id is string => Boolean(id));
          syncSaved(ids);
        }
      })
      .catch((error) => {
        if (active) setMessage(error.message);
      });
    return () => {
      active = false;
    };
  }, [visible, tab, syncSaved]);
  const action = async (id: string, operation: () => Promise<void>) => {
    if (locks.current.has(id)) return;
    locks.current.add(id);
    setPending((current) => [...current, id]);
    setMessage("");
    try {
      await operation();
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Não foi possível concluir a ação.",
      );
    } finally {
      locks.current.delete(id);
      setPending((current) => current.filter((item) => item !== id));
    }
  };
  const save = (id: string) =>
    void action(id, async () => {
      const enabled = !saved.includes(id);
      saveRevision.current++;
      await runtime().__BOOKRUSH_API__.save(id, enabled);
      setSaved((current) =>
        enabled ? [...current, id] : current.filter((item) => item !== id),
      );
      setMessage(
        enabled
          ? "Livro salvo na sua biblioteca."
          : "Livro removido da biblioteca.",
      );
    });
  const like = (id: string) =>
    void action(id, async () => {
      const enabled = !liked.includes(id);
      await runtime().__BOOKRUSH_API__.like(id, enabled);
      runtime().__BOOKRUSH_LIKES__[id] = enabled;
      setLiked((current) =>
        enabled ? [...current, id] : current.filter((item) => item !== id),
      );
      setCounts((current) => ({
        ...current,
        [id]: {
          likes: Math.max(0, (current[id]?.likes ?? 0) + (enabled ? 1 : -1)),
          comments: current[id]?.comments ?? 0,
        },
      }));
      try {
        await runtime().__BOOKRUSH_API__.refreshCounts(id);
      } catch {
        setMessage(
          "Curtida atualizada. Não foi possível atualizar a contagem agora.",
        );
      }
    });
  const onViewable = useCallback((id: string) => {
    if (viewed.current.has(id)) return;
    const book = runtime().__BOOKRUSH_BOOKS__.find((item) => item.id === id);
    if (!book) return;
    viewed.current.add(id);
    if (book.excerpt)
      runtime().__BOOKRUSH_API__.trackExcerpt(
        "EXCERPT_IMPRESSION",
        id,
        book.excerpt,
      );
    if (book.impressionId)
      void runtime()
        .__BOOKRUSH_API__.viewable(id)
        .catch(() => {
          viewed.current.delete(id);
        });
  }, []);
  const navigate = (nextTab: ReaderTab) => {
    setMessage("");
    setTab(nextTab);
  };
  const streak =
    plant?.streakDays ?? runtime().__BOOKRUSH_STREAK__?.currentDays;
  const openCatalogBook = (book: LibraryBook) =>
    void action(book.id, async () => {
      // The existing reader resolves IDs from its catalog array. Register books
      // saved outside the feed before delegating, retaining their actual metadata.
      if (!runtime().__BOOKRUSH_BOOKS__.some((item) => item.id === book.id))
        runtime().__BOOKRUSH_BOOKS__.push({
          ...book,
          quote: book.description,
          thumbnailUrl: book.coverUrl,
          a: "#067b8e",
          b: "#084256",
          keywords: [],
          likes: 0,
          comments: 0,
          shares: 0,
          pages: 0,
          match: 0,
        } as SourceBook);
      await runtime().__BOOKRUSH_LEGACY__.openBook(book.id, query);
      onLegacy();
    });
  return (
    <AppShell
      active={tab}
      query={query}
      onSearch={setQuery}
      onNavigate={navigate}
      userName={userName}
    >
      {tab === "recommendations" ? (
        <RecommendationsPage
          books={books}
          saved={saved}
          pending={pending}
          message={message}
          onSave={save}
          onOpen={(book) =>
            void action(book.id, async () => {
              await runtime().__BOOKRUSH_LEGACY__.openBook(book.id, query);
              onLegacy();
            })
          }
          onLucky={() => runtime().__BOOKRUSH_LEGACY__.openLucky()}
          onSearch={() => navigate("search")}
        />
      ) : tab === "search" ? (
        <SearchPage
          visible={visible}
          query={query}
          onQuery={setQuery}
          saved={saved}
          pending={pending}
          message={message}
          onSave={save}
          onOpen={openCatalogBook}
        />
      ) : tab === "library" ? (
        <LibraryPage
          visible={visible}
          saved={saved}
          pending={pending}
          message={message}
          onLoaded={syncSaved}
          onOpen={openCatalogBook}
          onRemove={save}
          onDiscover={() => navigate("feed")}
        />
      ) : [
          "plant",
          "profile",
          "privacy",
          "admin",
          "publisher",
          "prototype",
        ].includes(tab) ? (
        <AccountPage
          tab={
            tab as
              | "plant"
              | "profile"
              | "privacy"
              | "admin"
              | "publisher"
              | "prototype"
          }
          userName={userName}
          savedCount={saved.length}
          streak={streak}
          plant={plant}
          plantLoading={plantLoading}
          plantError={plantError}
          onNavigate={navigate}
        />
      ) : (
        <ExcerptFeed
          books={books}
          saved={saved}
          onSave={save}
          onOpen={(book) =>
            void action(book.id, async () => {
              await runtime().__BOOKRUSH_LEGACY__.openBook(book.id);
              onLegacy();
            })
          }
          live={{
            liked,
            counts,
            pending,
            message,
            onLike: like,
            onComments: (id) => {
              const book = books.find((item) => item.id === id);
              if (book) setCommentsBook({ id: book.id, title: book.title });
            },
            onShare: (id) => runtime().__BOOKRUSH_LEGACY__.openShare(id),
            onViewable,
            sidebar: (
              <>
                <Card>
                  <h2>
                    {typeof streak === "number"
                      ? `${streak} dias de sequência`
                      : "Sua leitura"}
                  </h2>
                  <p>Continue seu hábito, uma história de cada vez.</p>
                </Card>
                <Card>
                  <h2>Feito para você</h2>
                  <p>
                    Descubra histórias do seu catálogo e salve as que quiser ler
                    depois.
                  </p>
                </Card>
                <Card>
                  <h2>Sua biblioteca</h2>
                  <p>
                    {saved.length}{" "}
                    {saved.length === 1 ? "livro salvo" : "livros salvos"} para
                    sua próxima leitura.
                  </p>
                </Card>
              </>
            ),
          }}
        />
      )}
      {commentsBook && (
        <CommentsDrawer
          key={commentsBook.id}
          book={commentsBook}
          userName={userName}
          onClose={() => setCommentsBook(null)}
          onCount={onCommentCount}
          onProfile={(subject) => {
            setCommentsBook(null);
            runtime().__BOOKRUSH_LEGACY__.openProfile(subject);
            onLegacy();
          }}
        />
      )}
    </AppShell>
  );
}

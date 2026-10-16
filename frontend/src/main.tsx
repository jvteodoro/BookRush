import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { authClient, type AuthState } from "./auth";
import { createTelemetry } from "./telemetry";
import "./styles.css";
import "./styles/design-system.css";
import { ThemeProvider } from "./theme/ThemeProvider";
import { DesignSystemPreview } from "./design-system/DesignSystemPreview";
import { HomePreview } from "./design-system/HomePreview";
import "./styles/reader-shell.css";
import "./styles/reader-refinements.css";
import "./styles/search.css";
import "./styles/recommendations.css";
import "./styles/comments.css";
import { readiumBridge } from "./reader/readiumBridge";
import { AuthenticatedShell } from "./app/AuthenticatedShell";
import { AuthLanding } from "./app/AuthLanding";
import {
  loadCatalog,
  loadLibrary,
  loadRecent,
  loadRecommendationFeed,
  loadSocialCounts,
} from "./app/catalogApi";
import type { CatalogBook, FeedExcerpt, PageLoopBook } from "./app/types";
import type { ReadiumBridge } from "./app/types";
import { useLegacyNavigation } from "./app/useLegacyNavigation";
import { apiRoutes } from "./app/apiRoutes";

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: "loading" });
  const [error, setError] = useState("");
  const [feedReady, setFeedReady] = useState(false);
  const { legacyQuery, legacyVisible, readerTab, showLegacy } =
    useLegacyNavigation();
  useEffect(() => {
    let active = true;
    async function restore() {
      try {
        if (window.location.pathname === "/auth/callback") {
          const callback = await authClient.callback();
          window.history.replaceState({}, "", callback.returnTo);
          if (active) setAuth({ status: "authenticated", user: callback.user });
          return;
        }
        if (window.location.pathname === "/auth/silent-callback") {
          await authClient.silentCallback();
          return;
        }
        const user = await authClient.currentUser();
        if (active)
          setAuth(
            user ? { status: "authenticated", user } : { status: "anonymous" },
          );
      } catch (cause) {
        if (active)
          setAuth({
            status: "error",
            error:
              cause instanceof Error
                ? cause.message
                : "Falha ao restaurar a sessão.",
          });
      }
    }
    void restore();
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    if (auth.status !== "authenticated") return;
    let active = true;
    async function startPrototype() {
      try {
        const telemetry = createTelemetry(async (events) => {
          const response = await authClient.fetch(apiRoutes.behavior(), {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ events }),
          });
          if (!response.ok) throw new Error("telemetry rejected");
        });
        const catalog = await loadCatalog();
        const recommendations = await loadRecommendationFeed();
        if (!active) return;
        const byId = new Map(catalog.map((book) => [book.id, book]));
        const ordered = recommendations
          .map((item) => item.book)
          .filter((book): book is CatalogBook =>
            Boolean(book && byId.has(book.id)),
          );
        const source = ordered.length ? ordered : catalog;
        const palettes = [
          ["#82d4a4", "#213e35"],
          ["#74b9ff", "#202b52"],
          ["#f0a8bd", "#5a2337"],
          ["#c7d87a", "#26311f"],
          ["#d2aa6d", "#4a2e1f"],
        ];
        const books: PageLoopBook[] = await Promise.all(
          source.map(async (book, index) => {
            const recommendation = recommendations.find(
              (item) => item.book?.id === book.id,
            );
            return {
              id: book.id,
              title: book.canonicalTitle,
              author: "Catálogo BookRush",
              genre: book.originalLanguage?.toUpperCase() ?? "Clássico",
              keywords: [],
              match: Math.max(70, 96 - index * 2),
              a: palettes[index % palettes.length][0],
              b: palettes[index % palettes.length][1],
              quote:
                recommendation?.excerpt?.text?.trim() ||
                book.description ||
                `Descubra ${book.canonicalTitle} no catálogo BookRush.`,
              excerpt: recommendation?.excerpt ?? null,
              likes: 0,
              comments: 0,
              shares: 0,
              pages: 0,
              progress: 0,
              thumbnailUrl: book.thumbnailUrl
                ? await authClient.imageUrl(book.thumbnailUrl)
                : undefined,
              recommendationRequestId: recommendation?.recommendationRequestId,
              impressionId: recommendation?.impressionId,
              modelVersion: recommendation?.modelVersion,
              rank: recommendation?.rank,
            };
          }),
        );
        const socialCounts = await Promise.all(
          books.map((book) =>
            loadSocialCounts(book.id).catch(() => ({
              likes: 0,
              comments: 0,
              liked: false,
            })),
          ),
        );
        books.forEach((book, index) =>
          Object.assign(book, socialCounts[index]),
        );
        (
          window as Window & { __BOOKRUSH_LIKES__?: Record<string, boolean> }
        ).__BOOKRUSH_LIKES__ = Object.fromEntries(
          books.map((book, index) => [book.id, socialCounts[index].liked]),
        );
        const saved = new Set(await loadLibrary());
        const recent = await loadRecent();
        let submissions: unknown[] = [];
        try {
          const response = await authClient.fetch(
            apiRoutes.publisher.submissions(),
          );
          if (response.ok) submissions = (await response.json()) as unknown[];
        } catch {
          // Publisher access is optional for reader accounts; the portal shows
          // an empty state instead of preventing the reader feed from loading.
        }
        (
          window as Window & {
            __BOOKRUSH_BOOKS__?: PageLoopBook[];
            __BOOKRUSH_API__?: Record<string, unknown>;
          }
        ).__BOOKRUSH_BOOKS__ = books;
        (
          window as Window & { __BOOKRUSH_RECENT__?: string[] }
        ).__BOOKRUSH_RECENT__ = recent;
        (
          window as Window & { __BOOKRUSH_READIUM__?: ReadiumBridge }
        ).__BOOKRUSH_READIUM__ = readiumBridge;
        (
          window as Window & { __BOOKRUSH_PUBLISHER_SUBMISSIONS__?: unknown[] }
        ).__BOOKRUSH_PUBLISHER_SUBMISSIONS__ = submissions;
        try {
          const profileResponse = await authClient.fetch(
            apiRoutes.profile.self(),
          );
          (
            window as Window & { __BOOKRUSH_PROFILE__?: unknown }
          ).__BOOKRUSH_PROFILE__ = profileResponse.ok
            ? await profileResponse.json()
            : null;
        } catch {
          (
            window as Window & { __BOOKRUSH_PROFILE__?: unknown }
          ).__BOOKRUSH_PROFILE__ = null;
        }
        try {
          const [usersResponse, reportsResponse] = await Promise.all([
            authClient.fetch(apiRoutes.admin.users()),
            authClient.fetch(apiRoutes.admin.reports()),
          ]);
          (
            window as Window & { __BOOKRUSH_ADMIN_USERS__?: unknown[] }
          ).__BOOKRUSH_ADMIN_USERS__ = usersResponse.ok
            ? ((await usersResponse.json()) as unknown[])
            : [];
          (
            window as Window & { __BOOKRUSH_ADMIN_REPORTS__?: unknown[] }
          ).__BOOKRUSH_ADMIN_REPORTS__ = reportsResponse.ok
            ? ((await reportsResponse.json()) as unknown[])
            : [];
        } catch {
          (
            window as Window & { __BOOKRUSH_ADMIN_USERS__?: unknown[] }
          ).__BOOKRUSH_ADMIN_USERS__ = [];
          (
            window as Window & { __BOOKRUSH_ADMIN_REPORTS__?: unknown[] }
          ).__BOOKRUSH_ADMIN_REPORTS__ = [];
        }
        try {
          const streakResponse = await authClient.fetch(
            apiRoutes.reader.streak(),
          );
          (
            window as Window & { __BOOKRUSH_STREAK__?: unknown }
          ).__BOOKRUSH_STREAK__ = streakResponse.ok
            ? await streakResponse.json()
            : null;
        } catch {
          (
            window as Window & { __BOOKRUSH_STREAK__?: unknown }
          ).__BOOKRUSH_STREAK__ = null;
        }
        try {
          const plantResponse = await authClient.fetch(
            apiRoutes.reader.plant(),
          );
          (
            window as Window & { __BOOKRUSH_PLANT__?: unknown }
          ).__BOOKRUSH_PLANT__ = plantResponse.ok
            ? await plantResponse.json()
            : null;
        } catch {
          (
            window as Window & { __BOOKRUSH_PLANT__?: unknown }
          ).__BOOKRUSH_PLANT__ = null;
        }
        (
          window as Window & { __BOOKRUSH_API__?: Record<string, unknown> }
        ).__BOOKRUSH_API__ = {
          ...(window as Window & { __BOOKRUSH_API__?: Record<string, unknown> })
            .__BOOKRUSH_API__,
          saved,
          save: async (id: string, enabled: boolean) => {
            const response = await authClient.fetch(
              apiRoutes.reader.libraryBook(id),
              { method: enabled ? "PUT" : "DELETE" },
            );
            if (!response.ok)
              throw new Error("Não foi possível atualizar sua biblioteca.");
            if (enabled) saved.add(id);
            else saved.delete(id);
          },
          like: async (id: string, enabled: boolean) => {
            const response = await authClient.fetch(apiRoutes.social.like(id), {
              method: enabled ? "PUT" : "DELETE",
            });
            if (!response.ok)
              throw new Error("Não foi possível atualizar a curtida.");
          },
          share: async (id: string) => {
            const response = await authClient.fetch(
              apiRoutes.social.share(id),
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ channel: "copy-link" }),
              },
            );
            if (!response.ok)
              throw new Error("Não foi possível registrar o compartilhamento.");
          },
          comments: async (id: string) => {
            const response = await authClient.fetch(
              apiRoutes.social.comments(id),
            );
            return response.ok ? response.json() : [];
          },
          comment: async (id: string, text: string) => {
            const response = await authClient.fetch(
              apiRoutes.social.comments(id),
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ body: text }),
              },
            );
            if (!response.ok)
              throw new Error("Não foi possível publicar o comentário.");
          },
          refreshCounts: async (id: string) => {
            const counts = await loadSocialCounts(id);
            const book = books.find((item) => item.id === id);
            if (book) Object.assign(book, counts);
            const likes =
              (
                window as Window & {
                  __BOOKRUSH_LIKES__?: Record<string, boolean>;
                }
              ).__BOOKRUSH_LIKES__ ?? {};
            likes[id] = counts.liked;
            (
              window as Window & {
                __BOOKRUSH_LIKES__?: Record<string, boolean>;
              }
            ).__BOOKRUSH_LIKES__ = likes;
            window.dispatchEvent(
              new CustomEvent("bookrush:social-counts", {
                detail: { id, counts },
              }),
            );
            return counts;
          },
          profile: async () => {
            const response = await authClient.fetch(apiRoutes.profile.self());
            return response.ok ? response.json() : null;
          },
          publicProfile: async (subject: string) => {
            const response = await authClient.fetch(
              apiRoutes.profile.public(subject),
            );
            if (!response.ok)
              throw new Error("Não foi possível carregar este perfil.");
            const profile = (await response.json()) as {
              displayName?: string;
              subject_key?: string;
              bio?: string;
            };
            return profile.displayName?.trim() ? profile : null;
          },
          updateProfile: async (profile: {
            displayName?: string;
            bio?: string;
            isPublic?: boolean;
          }) => {
            const response = await authClient.fetch(apiRoutes.profile.self(), {
              method: "PUT",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(profile),
            });
            if (!response.ok)
              throw new Error("Não foi possível salvar o perfil.");
            return response.json();
          },
          privacy: async () => {
            const response = await authClient.fetch(
              apiRoutes.profile.privacy(),
            );
            if (!response.ok)
              throw new Error("Não foi possível carregar a privacidade.");
            return response.json();
          },
          updatePrivacy: async (privacy: {
            profileVisibility: string;
            activityVisibility: string;
            libraryVisibility: string;
            recentVisibility: string;
            socialGraphVisibility: string;
          }) => {
            const response = await authClient.fetch(
              apiRoutes.profile.privacy(),
              {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(privacy),
              },
            );
            if (!response.ok)
              throw new Error("Não foi possível salvar a privacidade.");
            return response.json();
          },
          follow: async (subject: string, enabled: boolean) => {
            const response = await authClient.fetch(
              apiRoutes.social.follow(subject),
              { method: enabled ? "PUT" : "DELETE" },
            );
            if (!response.ok)
              throw new Error("Não foi possível atualizar o acompanhamento.");
          },
          followStatus: async (subject: string) => {
            const response = await authClient.fetch(
              apiRoutes.social.follow(subject),
            );
            if (!response.ok)
              throw new Error("Não foi possível consultar o acompanhamento.");
            return ((await response.json()) as { following: boolean })
              .following;
          },
          adminUsers: async () => {
            const response = await authClient.fetch(apiRoutes.admin.users());
            if (!response.ok)
              throw new Error("Não foi possível carregar os usuários.");
            return response.json();
          },
          adminReports: async () => {
            const response = await authClient.fetch(apiRoutes.admin.reports());
            if (!response.ok)
              throw new Error("Não foi possível carregar as denúncias.");
            return response.json();
          },
          adminAudit: async (action: string, target: string) => {
            const response = await authClient.fetch(apiRoutes.admin.audit(), {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ action, target }),
            });
            if (!response.ok)
              throw new Error(
                "Não foi possível registrar a ação administrativa.",
              );
            return response.json();
          },
          adminModerate: async (target: string, decision: string) => {
            const response = await authClient.fetch(
              apiRoutes.admin.moderation(),
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ target, decision }),
              },
            );
            if (!response.ok)
              throw new Error(
                "Não foi possível registrar a decisão de moderação.",
              );
            return response.json();
          },
          publisherSubmissions: async () => {
            const response = await authClient.fetch(
              apiRoutes.publisher.submissions(),
            );
            if (!response.ok)
              throw new Error("Não foi possível carregar suas publicações.");
            return response.json();
          },
          createSubmission: async (title: string) => {
            const response = await authClient.fetch(
              apiRoutes.publisher.submissions(),
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ title }),
              },
            );
            if (!response.ok)
              throw new Error("Não foi possível salvar o rascunho.");
            return response.json();
          },
          submitSubmission: async (id: string) => {
            const response = await authClient.fetch(
              apiRoutes.publisher.submit(id),
              { method: "POST" },
            );
            if (!response.ok)
              throw new Error(
                "Não foi possível enviar a publicação para revisão.",
              );
            return response.json();
          },
          publisherMetrics: async (id: string) => {
            const response = await authClient.fetch(
              apiRoutes.publisher.metrics(id),
            );
            if (!response.ok)
              throw new Error("Não foi possível carregar as métricas.");
            return response.json();
          },
          uploadSubmissionFile: async (submissionId: string, file: File) => {
            const request = await authClient.fetch(
              apiRoutes.publisher.upload(submissionId),
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  filename: file.name,
                  contentType: file.type || "application/octet-stream",
                }),
              },
            );
            if (!request.ok)
              throw new Error("Não foi possível reservar o upload.");
            const reservation = (await request.json()) as {
              uploadId: string;
              url: string;
            };
            const put = await fetch(reservation.url, {
              method: "PUT",
              headers: {
                "Content-Type": file.type || "application/octet-stream",
              },
              body: file,
            });
            if (!put.ok) throw new Error("O storage recusou o upload.");
            const digest = await crypto.subtle.digest(
              "SHA-256",
              await file.arrayBuffer(),
            );
            const sha256 = Array.from(new Uint8Array(digest), (byte) =>
              byte.toString(16).padStart(2, "0"),
            ).join("");
            const complete = await authClient.fetch(
              apiRoutes.publisher.finalize(submissionId, reservation.uploadId),
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ sha256 }),
              },
            );
            if (!complete.ok)
              throw new Error("Não foi possível finalizar o upload.");
            return complete.json();
          },
          progress: async (
            id: string,
            percent: number,
            positionCodepoint: number,
          ) => {
            await authClient.fetch(apiRoutes.reader.progress(id), {
              method: "PUT",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ percent, positionCodepoint }),
            });
          },
          bookmark: async (id: string, positionCodepoint: number) => {
            await authClient.fetch(apiRoutes.reader.bookmarks(id), {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ positionCodepoint }),
            });
          },
          open: async (id: string) => {
            await authClient.fetch(apiRoutes.reader.opened(id), {
              method: "POST",
            });
            await authClient.fetch(apiRoutes.behavior(), {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                events: [
                  {
                    eventKey: `BOOK_OPEN:${id}:${Date.now()}`,
                    eventType: "BOOK_OPEN",
                    bookId: id,
                    occurredAt: new Date().toISOString(),
                    payload: {},
                  },
                ],
              }),
            });
          },
          chapters: async (id: string) => {
            const response = await authClient.fetch(
              apiRoutes.reader.chapters(id),
            );
            return response.ok ? response.json() : [];
          },
          viewable: async (id: string) => {
            const item = recommendations.find(
              (candidate) => candidate.book?.id === id,
            );
            if (item?.impressionId) {
              const response = await authClient.fetch(
                apiRoutes.reader.impressionViewable(item.impressionId),
                { method: "POST" },
              );
              if (!response.ok)
                throw new Error("Não foi possível registrar a visualização.");
            }
          },
          trackExcerpt: (
            eventType: string,
            bookId: string,
            excerpt?: FeedExcerpt | null,
            payload: Record<string, unknown> = {},
          ) => {
            telemetry.track(eventType, bookId, {
              ...payload,
              excerptId: excerpt?.id,
              sourceAssetVersionId: excerpt?.sourceAssetVersionId,
            });
          },
        };
        document.body.dataset.mode = "web";
        document.body.dataset.start = "feed";
        if (document.querySelector("script[data-bookrush-pageloop]")) {
          if (active) setFeedReady(true);
          return;
        }
        document.body.dataset.mode =
          new URLSearchParams(window.location.search).get("mode") === "app"
            ? "app"
            : "web";
        document.body.dataset.start = "feed";
        if (document.querySelector("script[data-bookrush-pageloop]")) return;
        const style = document.createElement("link");
        style.id = "bookrush-pageloop-style";
        style.rel = "stylesheet";
        style.href = "/pageloop.css?v=20261011-public-profile";
        document.head.appendChild(style);
        const thumbnailStyle = document.createElement("link");
        thumbnailStyle.id = "bookrush-thumbnail-style";
        thumbnailStyle.rel = "stylesheet";
        thumbnailStyle.href = "/thumbnails.css?v=20261006-gutenberg";
        document.head.appendChild(thumbnailStyle);
        const script = document.createElement("script");
        script.dataset.bookrushPageloop = "true";
        script.src = "/pageloop.js?v=20261012-design-system-bridge";
        script.async = true;
        script.onload = () => {
          if (active) setFeedReady(true);
        };
        script.onerror = () => {
          if (active)
            setError(
              "Não foi possível carregar a navegação. Recarregue a página para tentar novamente.",
            );
        };
        document.body.appendChild(script);
      } catch (cause) {
        if (active)
          setError(
            cause instanceof Error
              ? cause.message
              : "Não foi possível carregar o catálogo.",
          );
      }
    }
    void startPrototype();
    return () => {
      active = false;
    };
  }, [auth.status]);
  async function login() {
    setAuth({ status: "loading" });
    try {
      await authClient.login(window.location.pathname + window.location.search);
    } catch (cause) {
      setAuth({
        status: "error",
        error:
          cause instanceof Error
            ? cause.message
            : "Não foi possível iniciar o login.",
      });
    }
  }
  if (auth.status === "authenticated")
    return (
      <AuthenticatedShell
        error={error}
        feedReady={feedReady}
        legacyQuery={legacyQuery}
        legacyVisible={legacyVisible}
        onRetry={() => window.location.reload()}
        onShowLegacy={showLegacy}
        readerTab={readerTab}
        user={auth.user}
      />
    );
  if (auth.status === "loading")
    return (
      <div className="splash">
        <div className="brand-mark">B</div>
        <p>Preparando seu feed…</p>
      </div>
    );
  return <AuthLanding auth={auth} onLogin={() => void login()} />;
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      {import.meta.env.DEV && window.location.pathname === "/design-system" ? (
        <DesignSystemPreview />
      ) : import.meta.env.DEV &&
        window.location.pathname === "/home-preview" ? (
        <HomePreview />
      ) : (
        <App />
      )}
    </ThemeProvider>
  </StrictMode>,
);

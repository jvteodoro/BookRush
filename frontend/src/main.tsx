import { bookMetadata } from "./reader/bookMetadata";
import { categoryLabel } from "./reader/bookCategories";
import { recordOpenedBook } from "./reader/seen";
import { loadFollowerStats } from "./reader/followers";
import { mountAppearance } from "./reader/appearancePanel";
import { mountReaderPanels } from "./reader/readerPanels";
import "./styles/reader-appearance.css";
import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { authClient, setProductGate, type AuthState } from "./auth";
import { createTelemetry } from "./telemetry";
import "./styles.css";
import "./styles/session.css";
import "./styles/design-system.css";
import { ThemeProvider } from "./theme/ThemeProvider";
import { DesignSystemPreview } from "./design-system/DesignSystemPreview";
import { HomePreview } from "./design-system/HomePreview";
import "./styles/reader-shell.css";
import "./styles/reader-refinements.css";
import "@bookrush/ui/sidebar.css";
import "@bookrush/ui/layout.css";
import "./styles/search.css";
import "./styles/recommendations.css";
import "./styles/comments.css";
import { readiumBridge } from "./reader/lazyReadiumBridge";
import { createBookmarksClient } from "./reader/bookmarks";
import { bookmarkDialogs } from "./reader/bookmarkDialogs";
import { AuthenticatedShell } from "./app/AuthenticatedShell";
import {
  allowsProduct,
  createSessionGate,
  type SessionView,
} from "../../packages/session/session";
import type { User } from "oidc-client-ts";
import { RestrictedSession } from "./app/RestrictedSession";
import { FamilyScreen } from "./family/FamilyScreen";
import { AuthLanding } from "./app/AuthLanding";
import {
  loadLibrary,
  loadRecent,
  loadRecommendationFeed,
  loadSocialCounts,
} from "./app/catalogApi";
import type { CatalogBook, FeedExcerpt, PageLoopBook } from "./app/types";
import type { ReadiumBridge } from "./app/types";
import { useLegacyNavigation } from "./app/useLegacyNavigation";
import { apiRoutes } from "./app/apiRoutes";
import { readerSessionOrigin } from "./reader/readerSessionOrigin";

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: "loading" });
  useEffect(() => {
    let active = true;
    async function restore() {
      try {
        if (window.location.pathname === "/auth/callback") {
          const callback = await authClient.callback();
          window.history.replaceState({}, "", callback.returnTo);
          window.dispatchEvent(new Event("bookrush:path-changed"));
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
  if (auth.status === "authenticated" && auth.user)
    return (
      <SessionApp
        key={`${auth.user.profile.iss}:${auth.user.profile.sub}`}
        user={auth.user}
        onLogin={() => void login()}
        onLogout={() => {
          setAuth({ status: "anonymous" });
          void authClient.logout().catch(() => undefined);
        }}
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

function SessionApp({
  user,
  onLogin,
  onLogout,
}: {
  user: User;
  onLogin: () => void;
  onLogout: () => void;
}) {
  const [view, setView] = useState<SessionView>({ status: "loading" });
  const [gate, setGate] = useState<ReturnType<typeof createSessionGate>>();
  useEffect(() => {
    let product: AbortController | undefined;
    let lastDenial = 0;
    const session = createSessionGate(
      authClient.fetch.bind(authClient),
      (next) => {
        if (allowsProduct(next)) product ??= new AbortController();
        else {
          product?.abort();
          product = undefined;
        }
        setView(next);
      },
      () => document.visibilityState !== "hidden",
    );
    setGate(session);
    setProductGate({
      signal: () => (session.allowsProduct() ? product?.signal : undefined),
      observe(status) {
        if (
          (status === 401 || status === 403 || status === 503) &&
          Date.now() - lastDenial > 3000
        ) {
          lastDenial = Date.now();
          session.refresh(true);
        }
      },
    });
    const resume = () => session.refresh();
    window.addEventListener("focus", resume);
    document.addEventListener("visibilitychange", resume);
    session.refresh();
    return () => {
      session.dispose();
      product?.abort();
      setProductGate({ signal: () => undefined, observe: () => undefined });
      window.removeEventListener("focus", resume);
      document.removeEventListener("visibilitychange", resume);
    };
  }, []);
  if (view.status === "ready" && allowsProduct(view))
    return <ProductApp user={user} onLogout={onLogout} />;
  if (view.status === "ready")
    return (
      <RestrictedSession
        session={view.session}
        onRefresh={() => gate?.refresh(true)}
        onLogout={onLogout}
      />
    );
  return (
    <main className="landing restricted-session">
      <p role="status">
        {view.status === "loading"
          ? "Verificando seu acesso…"
          : view.authenticate
            ? "Sua sessão precisa ser renovada."
            : "Não foi possível verificar seu acesso. Tente novamente."}
      </p>
      {view.status === "error" && (
        <button
          className="primary-button"
          onClick={view.authenticate ? onLogin : () => gate?.refresh(true)}
        >
          {view.authenticate ? "Entrar novamente" : "Tentar novamente"}
        </button>
      )}
      <button onClick={onLogout}>Sair da conta</button>
    </main>
  );
}

function ProductApp({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [error, setError] = useState("");
  const [familyOpen, setFamilyOpen] = useState(false);
  const [feedReady, setFeedReady] = useState(false);
  const { bookPath, legacyQuery, legacyVisible, readerTab, showLegacy } =
    useLegacyNavigation();
  useEffect(() => {
    let active = true;
    const urls = new Set<string>();
    let telemetry: ReturnType<typeof createTelemetry> | undefined;
    let ownsLegacy = false;
    const productFetch: typeof authClient.fetch = (path, init) => {
      if (!active)
        return Promise.reject(
          new DOMException("Session changed", "AbortError"),
        );
      return authClient.fetch(path, init);
    };
    async function startPrototype() {
      try {
        const sender = createTelemetry(async (events) => {
          const response = await productFetch(apiRoutes.behavior(), {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ events }),
          });
          if (!response.ok) throw new Error("telemetry rejected");
        });
        telemetry = sender;
        const recommendations = await loadRecommendationFeed().catch(() => []);
        if (!active) return;
        const recommendationFor = (bookId: string) =>
          recommendations.find((item) => item.book?.id === bookId);
        const correlationHeaders = (bookId: string): Record<string, string> => {
          const recommendation = recommendationFor(bookId);
          const headers: Record<string, string> = {};
          if (recommendation?.recommendationItemId)
            headers["X-BookRush-Recommendation-Item"] =
              recommendation.recommendationItemId;
          if (recommendation?.impressionId)
            headers["X-BookRush-Impression"] = recommendation.impressionId;
          return headers;
        };
        const ordered = recommendations
          .map((item) => item.book)
          .filter((book): book is CatalogBook => Boolean(book));
        // The live feed is the source of truth.  Do not fall back to the full
        // catalog: analytics-required deployments must never show books that
        // have no eligible recommendation/excerpt.
        const source = ordered;
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
              ...bookMetadata(book),
              genre: categoryLabel(book.categories),
              categories: book.categories ?? [],
              language: book.originalLanguage,
              description: book.description ?? "",
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
              recommendationItemId: recommendation?.recommendationItemId,
              impressionId: recommendation?.impressionId,
              modelVersion: recommendation?.modelVersion,
              rank: recommendation?.rank,
            };
          }),
        );
        for (const book of books) {
          if (book.thumbnailUrl?.startsWith("blob:")) {
            if (active) urls.add(book.thumbnailUrl);
            else URL.revokeObjectURL(book.thumbnailUrl);
          }
        }
        if (!active) return;
        const socialCounts = await Promise.all(
          books.map((book) =>
            loadSocialCounts(book.id).catch(() => ({
              likes: 0,
              comments: 0,
              liked: false,
            })),
          ),
        );
        if (!active) return;
        books.forEach((book, index) =>
          Object.assign(book, socialCounts[index]),
        );
        (
          window as Window & { __BOOKRUSH_LIKES__?: Record<string, boolean> }
        ).__BOOKRUSH_LIKES__ = Object.fromEntries(
          books.map((book, index) => [book.id, socialCounts[index].liked]),
        );
        const saved = new Set(await loadLibrary());
        if (!active) return;
        const recent = await loadRecent();
        if (!active) return;
        // Publisher and administration APIs belong to the platform realm and
        // are intentionally not called by the product-realm reader session.
        // The dedicated admin console obtains the correct platform token.
        const submissions: unknown[] = [];
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
          const profileResponse = await productFetch(apiRoutes.profile.self());
          const value = profileResponse.ok
            ? await profileResponse.json()
            : null;
          if (!active) return;
          (
            window as Window & { __BOOKRUSH_PROFILE__?: unknown }
          ).__BOOKRUSH_PROFILE__ = value;
        } catch {
          if (!active) return;
          (
            window as Window & { __BOOKRUSH_PROFILE__?: unknown }
          ).__BOOKRUSH_PROFILE__ = null;
        }
        (
          window as Window & { __BOOKRUSH_ADMIN_USERS__?: unknown[] }
        ).__BOOKRUSH_ADMIN_USERS__ = [];
        (
          window as Window & { __BOOKRUSH_ADMIN_REPORTS__?: unknown[] }
        ).__BOOKRUSH_ADMIN_REPORTS__ = [];
        try {
          const streakResponse = await productFetch(apiRoutes.reader.streak());
          const value = streakResponse.ok ? await streakResponse.json() : null;
          if (!active) return;
          (
            window as Window & { __BOOKRUSH_STREAK__?: unknown }
          ).__BOOKRUSH_STREAK__ = value;
        } catch {
          if (!active) return;
          (
            window as Window & { __BOOKRUSH_STREAK__?: unknown }
          ).__BOOKRUSH_STREAK__ = null;
        }
        try {
          const plantResponse = await productFetch(apiRoutes.reader.plant());
          const value = plantResponse.ok ? await plantResponse.json() : null;
          if (!active) return;
          (
            window as Window & { __BOOKRUSH_PLANT__?: unknown }
          ).__BOOKRUSH_PLANT__ = value;
        } catch {
          if (!active) return;
          (
            window as Window & { __BOOKRUSH_PLANT__?: unknown }
          ).__BOOKRUSH_PLANT__ = null;
        }
        const bookmarksClient = createBookmarksClient(
          (url, init) => productFetch(url, init),
          apiRoutes.reader.bookmarks,
        );
        (
          window as Window & { __BOOKRUSH_API__?: Record<string, unknown> }
        ).__BOOKRUSH_API__ = {
          ...(window as Window & { __BOOKRUSH_API__?: Record<string, unknown> })
            .__BOOKRUSH_API__,
          saved,
          readerSessionCorrelation: (bookId: string) =>
            readerSessionOrigin.activeFor(bookId),
          clearReaderSessionOrigin: () => {
            readerSessionOrigin.clear();
          },
          save: async (id: string, enabled: boolean) => {
            const response = await productFetch(
              apiRoutes.reader.libraryBook(id),
              {
                method: enabled ? "PUT" : "DELETE",
                headers: correlationHeaders(id),
              },
            );
            if (!response.ok)
              throw new Error("Não foi possível atualizar sua biblioteca.");
            if (enabled) saved.add(id);
            else saved.delete(id);
          },
          like: async (id: string, enabled: boolean) => {
            const response = await productFetch(apiRoutes.social.like(id), {
              method: enabled ? "PUT" : "DELETE",
              headers: correlationHeaders(id),
            });
            if (!response.ok)
              throw new Error("Não foi possível atualizar a curtida.");
          },
          share: async (id: string) => {
            const response = await productFetch(apiRoutes.social.share(id), {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                ...correlationHeaders(id),
              },
              body: JSON.stringify({ channel: "copy-link" }),
            });
            if (!response.ok)
              throw new Error("Não foi possível registrar o compartilhamento.");
          },
          comments: async (id: string) => {
            const response = await productFetch(apiRoutes.social.comments(id));
            return response.ok ? response.json() : [];
          },
          comment: async (id: string, text: string) => {
            const response = await productFetch(apiRoutes.social.comments(id), {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ body: text }),
            });
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
            const response = await productFetch(apiRoutes.profile.self());
            return response.ok ? response.json() : null;
          },
          publicProfile: async (subject: string) => {
            const response = await productFetch(
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
            const response = await productFetch(apiRoutes.profile.self(), {
              method: "PUT",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(profile),
            });
            if (!response.ok)
              throw new Error("Não foi possível salvar o perfil.");
            return response.json();
          },
          privacy: async () => {
            const response = await productFetch(apiRoutes.profile.privacy());
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
            const response = await productFetch(apiRoutes.profile.privacy(), {
              method: "PUT",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(privacy),
            });
            if (!response.ok)
              throw new Error("Não foi possível salvar a privacidade.");
            return response.json();
          },
          followerStats: (subject?: string) => loadFollowerStats(subject),
          follow: async (subject: string, enabled: boolean) => {
            const response = await productFetch(
              apiRoutes.social.follow(subject),
              { method: enabled ? "PUT" : "DELETE" },
            );
            if (!response.ok)
              throw new Error("Não foi possível atualizar o acompanhamento.");
          },
          followStatus: async (subject: string) => {
            const response = await productFetch(
              apiRoutes.social.follow(subject),
            );
            if (!response.ok)
              throw new Error("Não foi possível consultar o acompanhamento.");
            return ((await response.json()) as { following: boolean })
              .following;
          },
          adminUsers: async () => {
            const response = await productFetch(apiRoutes.admin.users());
            if (!response.ok)
              throw new Error("Não foi possível carregar os usuários.");
            return response.json();
          },
          adminReports: async () => {
            const response = await productFetch(apiRoutes.admin.reports());
            if (!response.ok)
              throw new Error("Não foi possível carregar as denúncias.");
            return response.json();
          },
          adminAudit: async (action: string, target: string) => {
            const response = await productFetch(apiRoutes.admin.audit(), {
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
            const response = await productFetch(apiRoutes.admin.moderation(), {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ target, decision }),
            });
            if (!response.ok)
              throw new Error(
                "Não foi possível registrar a decisão de moderação.",
              );
            return response.json();
          },
          publisherSubmissions: async () => {
            const response = await productFetch(
              apiRoutes.publisher.submissions(),
            );
            if (!response.ok)
              throw new Error("Não foi possível carregar suas publicações.");
            return response.json();
          },
          createSubmission: async (title: string) => {
            const response = await productFetch(
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
            const response = await productFetch(
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
            const response = await productFetch(
              apiRoutes.publisher.metrics(id),
            );
            if (!response.ok)
              throw new Error("Não foi possível carregar as métricas.");
            return response.json();
          },
          uploadSubmissionFile: async (submissionId: string, file: File) => {
            const request = await productFetch(
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
            const complete = await productFetch(
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
          bookmarks: bookmarksClient.list,
          bookmark: bookmarksClient.save,
          renameBookmark: bookmarksClient.rename,
          deleteBookmark: bookmarksClient.remove,
          open: async (id: string, fromRecommendation = false) => {
            const headers = fromRecommendation ? correlationHeaders(id) : {};
            if (fromRecommendation) {
              readerSessionOrigin.request({ bookId: id, headers });
              return;
            }
            await recordOpenedBook(id, readerSessionOrigin.activeFor(id));
          },
          chapters: async (id: string) => {
            const response = await productFetch(apiRoutes.reader.chapters(id));
            return response.ok ? response.json() : [];
          },
          viewable: async (id: string) => {
            const item = recommendationFor(id);
            if (item?.impressionId) {
              const response = await productFetch(
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
            sender.track(eventType, bookId, {
              ...payload,
              recommendationItemId:
                recommendationFor(bookId)?.recommendationItemId,
              impressionId: recommendationFor(bookId)?.impressionId,
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
        Object.assign(window, {
          __BOOKRUSH_BOOKMARK_DIALOGS__: bookmarkDialogs,
          __BOOKRUSH_APPEARANCE__: {
            mount: mountAppearance,
            panels: mountReaderPanels,
          },
        });
        const script = document.createElement("script");
        script.dataset.bookrushPageloop = "true";
        script.src = "/pageloop.js?v=20261116-reader-named-bookmarks";
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
        ownsLegacy = true;
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
      telemetry?.dispose();
      for (const url of urls) URL.revokeObjectURL(url);
      const globals = window as unknown as Window & Record<string, unknown>;
      if (ownsLegacy) {
        (globals.__BOOKRUSH_LEGACY_DISPOSE__ as (() => void) | undefined)?.();
        document.querySelector("script[data-bookrush-pageloop]")?.remove();
        document.getElementById("bookrush-pageloop-style")?.remove();
        document.querySelector('link[href^="/thumbnails.css"]')?.remove();
      }
      void readiumBridge.unmount();
      for (const key of [
        "API",
        "BOOKS",
        "RECENT",
        "READIUM",
        "PROFILE",
        "LIKES",
        "STREAK",
        "PLANT",
        "ADMIN_USERS",
        "ADMIN_REPORTS",
        "PUBLISHER_SUBMISSIONS",
        "BOOKMARK_DIALOGS",
        "APPEARANCE",
      ]) {
        delete globals[`__BOOKRUSH_${key}__`];
      }
    };
  }, []);
  return (
    <>
      <AuthenticatedShell
        error={error}
        bookPath={bookPath}
        feedReady={feedReady}
        legacyQuery={legacyQuery}
        legacyVisible={legacyVisible}
        onRetry={() => window.location.reload()}
        onShowLegacy={showLegacy}
        onFamily={() => setFamilyOpen(true)}
        readerTab={readerTab}
        user={user}
        onLogout={onLogout}
      />
      {familyOpen && <FamilyScreen onClose={() => setFamilyOpen(false)} />}
    </>
  );
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

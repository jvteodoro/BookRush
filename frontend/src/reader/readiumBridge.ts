import { HttpFetcher, Manifest, Publication } from "@readium/shared";
import { WebPubNavigator, WebPubPreferences } from "@readium/navigator";
import { authClient } from "../auth";
import type {
  ReadiumBridge,
  ReaderChapter,
  ReaderPreferences,
} from "../app/types";

const defaultPreferences: ReaderPreferences = {
  fontFamily: "serif",
  fontSize: 100,
  lineHeight: 1.8,
  foreground: "#29261f",
  background: "#f7f4ee",
};

let navigator: any;
let manifest: any;
let publication: Publication | undefined;
let container: HTMLElement | undefined;
let frame: HTMLIFrameElement | undefined;
let frameWindow: Window | undefined;
let preferences = defaultPreferences;

const fontFamilies = {
  serif: 'Georgia, "Times New Roman", serif',
  sans: "Arial, Helvetica, sans-serif",
  dyslexic: "OpenDyslexic, Arial, sans-serif",
};

function toChapters(links: any[] = [], depth = 0): ReaderChapter[] {
  return links.flatMap((link, index) => {
    const href = String(link.href ?? "");
    if (!href) return [];
    return [
      {
        id: href,
        href,
        title: String(link.title ?? `Capítulo ${index + 1}`),
        depth,
      },
      ...toChapters(link.children?.items ?? link.children ?? [], depth + 1),
    ];
  });
}

function applyPreferences(next: ReaderPreferences) {
  preferences = next;
  container?.style.setProperty("background", next.background);
  if (navigator?.submitPreferences) {
    void navigator
      .submitPreferences(
        new WebPubPreferences({
          fontFamily: fontFamilies[next.fontFamily],
          lineHeight: next.lineHeight,
        }),
      )
      .catch(() => undefined);
  }
  const document =
    frameWindow?.document ??
    (frame ?? container?.querySelector("iframe"))?.contentDocument;
  if (!document) return;
  document.getElementById("bookrush-reader-preferences")?.remove();
  const style = document.createElement("style");
  style.id = "bookrush-reader-preferences";
  style.textContent = `
    html, body { background: ${next.background} !important; color: ${next.foreground} !important; }
    body, body * { font-family: ${fontFamilies[next.fontFamily]} !important; }
    body { font-size: ${next.fontSize}% !important; line-height: ${next.lineHeight} !important; }
    p, li, blockquote, h1, h2, h3, h4, h5, h6 { color: ${next.foreground} !important; }
    p, li, blockquote { line-height: ${next.lineHeight} !important; }
  `;
  document.head.appendChild(style);
}

export const readiumBridge: ReadiumBridge = {
  async mount(bookId, nextContainer, onLocatorChanged) {
    await this.unmount();
    container = nextContainer;
    const response = await authClient.fetch(
      `/api/v1/content/books/${encodeURIComponent(bookId)}/publication.json`,
    );
    if (!response.ok) {
      throw new Error(`Publicação indisponível (HTTP ${response.status}).`);
    }
    const raw = await response.json();
    manifest = raw;
    const parsedManifest = Manifest.deserialize(raw);
    if (!parsedManifest?.readingOrder?.items?.length) {
      throw new Error("Este livro ainda não possui conteúdo reader-ready.");
    }
    const fetcher = new HttpFetcher(
      (input, init) =>
        authClient.fetch(String(input), { ...init, cache: "no-store" }),
      window.location.origin,
    );
    publication = new Publication({ manifest: parsedManifest, fetcher });
    navigator = new WebPubNavigator(nextContainer, publication, {
      frameLoaded: (nextFrame: Window) => {
        frameWindow = nextFrame;
        frame = nextContainer.querySelector("iframe") ?? undefined;
        applyPreferences(preferences);
      },
      positionChanged: (locator: any) =>
        onLocatorChanged?.(
          locator.locations?.totalProgression ??
            locator.locations?.progression ??
            0,
        ),
      timelineItemChanged: () => undefined,
      tap: () => false,
      click: () => false,
      zoom: () => undefined,
      scroll: () => undefined,
      customEvent: () => undefined,
      handleLocator: () => false,
      textSelected: () => undefined,
      contentProtection: () => undefined,
      contextMenu: () => undefined,
      peripheral: () => undefined,
    } as any);
    await navigator.load();
    applyPreferences(preferences);
  },

  async unmount() {
    if (navigator) await navigator.destroy();
    navigator = undefined;
    manifest = undefined;
    publication = undefined;
    frame = undefined;
    frameWindow = undefined;
    container = undefined;
  },

  async chapters() {
    const toc = publication?.manifest.toc?.items ?? manifest?.toc ?? [];
    return toChapters(toc);
  },

  async goToChapter(chapterId) {
    const links =
      publication?.readingOrder?.items ?? manifest?.readingOrder ?? [];
    const toc = publication?.manifest.toc?.items ?? manifest?.toc ?? [];
    const chapter = toChapters(toc).find(
      (item) => item.id === chapterId || item.href === chapterId,
    );
    const link =
      [...links].find((item: any) =>
        chapter
          ? String(item.href).split("#")[0] === chapter.href.split("#")[0]
          : String(item.href).includes(`/chapters/${chapterId}/content`),
      ) ?? (chapter ? { href: chapter.href, title: chapter.title } : undefined);
    if (!link || !navigator) return false;
    if (typeof navigator.goLink === "function") {
      return new Promise<boolean>((resolve) =>
        navigator.goLink(link, false, (ok: boolean) => resolve(Boolean(ok))),
      );
    }
    if (typeof navigator.goTo === "function") {
      return Boolean(await navigator.goTo(link));
    }
    return false;
  },

  async next() {
    if (typeof navigator?.goForward !== "function") return false;
    return new Promise<boolean>((resolve) =>
      navigator.goForward(false, (ok: boolean) => resolve(Boolean(ok))),
    );
  },

  async previous() {
    if (typeof navigator?.goBackward !== "function") return false;
    return new Promise<boolean>((resolve) =>
      navigator.goBackward(false, (ok: boolean) => resolve(Boolean(ok))),
    );
  },

  applyPreferences,
};

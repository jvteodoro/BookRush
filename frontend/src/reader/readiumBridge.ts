import { HttpFetcher, Manifest, Publication } from "@readium/shared";
import { WebPubNavigator } from "@readium/navigator";
import { authClient } from "../auth";
import type { ReadiumBridge, ReaderPreferences } from "../app/types";

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
let preferences = defaultPreferences;

function applyPreferences(next: ReaderPreferences) {
  preferences = next;
  const document = (frame ?? container?.querySelector("iframe"))
    ?.contentDocument;
  if (!document) return;
  document.getElementById("bookrush-reader-preferences")?.remove();
  const style = document.createElement("style");
  style.id = "bookrush-reader-preferences";
  const fonts = {
    serif: 'Georgia, "Times New Roman", serif',
    sans: "Arial, Helvetica, sans-serif",
    dyslexic: "OpenDyslexic, Arial, sans-serif",
  };
  style.textContent = `
    html, body { background: ${next.background} !important; color: ${next.foreground} !important; }
    body, body * { font-family: ${fonts[next.fontFamily]} !important; }
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
      frameLoaded: (nextFrame: HTMLIFrameElement) => {
        frame = nextFrame ?? nextContainer.querySelector("iframe") ?? undefined;
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
    container = undefined;
  },

  async goToChapter(chapterId) {
    const links =
      publication?.readingOrder?.items ?? manifest?.readingOrder ?? [];
    const link = [...links].find((item: any) =>
      String(item.href).includes(`/chapters/${chapterId}/content`),
    );
    if (!link || typeof navigator?.goTo !== "function") return false;
    return Boolean(await navigator.goTo(link));
  },

  async next() {
    if (typeof navigator?.goForward !== "function") return false;
    return Boolean(await navigator.goForward());
  },

  async previous() {
    if (typeof navigator?.goBackward !== "function") return false;
    return Boolean(await navigator.goBackward());
  },

  applyPreferences,
};

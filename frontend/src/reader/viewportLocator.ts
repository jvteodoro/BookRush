import type { ReadingLocator } from "./readingProgress";

type ViewportAnchor = {
  version: 1;
  selector: string;
  childIndex: number;
  offset: number;
  top: number;
  quote: string;
};

function selectorFor(element: Element): string {
  const parts: string[] = [];
  while (element.localName !== "body") {
    const parent = element.parentElement;
    if (!parent) return "";
    const siblings = [...parent.children].filter(
      (item) => item.localName === element.localName,
    );
    parts.unshift(
      `${element.localName}:nth-of-type(${siblings.indexOf(element) + 1})`,
    );
    element = parent;
  }
  return ["body", ...parts].join(" > ");
}

/** Anchor a text character on the first visible line, rather than a layout ratio. */
export function captureViewportLocator(
  wnd: Window | undefined,
  locator: ReadingLocator,
): ReadingLocator {
  if (!wnd?.document.body) return locator;
  const doc = wnd.document;
  const walker = doc.createTreeWalker(doc.body, 4 /* SHOW_TEXT */);
  let node: Node | null;
  while ((node = walker.nextNode())) {
    const text = node as Text;
    if (!text.data.trim() || !text.parentElement) continue;
    const range = doc.createRange();
    range.selectNodeContents(text);
    if (
      ![...range.getClientRects()].some(
        (rect) =>
          rect.width > 0 && rect.bottom > 0 && rect.top < wnd.innerHeight,
      )
    )
      continue;
    let low = 0;
    let high = text.length - 1;
    while (low < high) {
      const middle = Math.floor((low + high) / 2);
      range.setStart(text, middle);
      range.setEnd(text, middle + 1);
      if (range.getBoundingClientRect().bottom > 0) high = middle;
      else low = middle + 1;
    }
    range.setStart(text, low);
    range.setEnd(text, low + 1);
    const rect = range.getBoundingClientRect();
    const selector = selectorFor(text.parentElement);
    if (
      !selector ||
      rect.width === 0 ||
      rect.bottom <= 0 ||
      rect.top >= wnd.innerHeight
    )
      continue;
    const anchor: ViewportAnchor = {
      version: 1,
      selector,
      childIndex: [...text.parentElement.childNodes].indexOf(text),
      offset: low,
      top: rect.top,
      quote: text.data.slice(low, low + 32),
    };
    const scroller = doc.scrollingElement;
    return {
      ...locator,
      locations: {
        ...locator.locations,
        progression: scroller?.scrollHeight
          ? scroller.scrollTop / scroller.scrollHeight
          : locator.locations.progression,
        bookrushViewport: anchor,
      },
    };
  }
  return locator;
}

export async function waitForReaderLayout(wnd: Window | undefined) {
  if (!wnd) return;
  await wnd.document.fonts?.ready;
  await new Promise<void>((resolve) =>
    wnd.requestAnimationFrame(() => wnd.requestAnimationFrame(() => resolve())),
  );
}

/** Restore only a matching node/text; old or incompatible anchors retain SDK fallback. */
export function restoreViewportLocator(
  wnd: Window | undefined,
  locator: ReadingLocator | null,
): boolean {
  if (!wnd || !locator) return false;
  const anchor = (locator.locations as Record<string, unknown>)
    .bookrushViewport as ViewportAnchor | undefined;
  if (
    !anchor ||
    anchor.version !== 1 ||
    typeof anchor.selector !== "string" ||
    !Number.isInteger(anchor.childIndex) ||
    !Number.isInteger(anchor.offset) ||
    anchor.childIndex < 0 ||
    anchor.offset < 0 ||
    !Number.isFinite(anchor.top) ||
    Math.abs(anchor.top) > wnd.innerHeight ||
    typeof anchor.quote !== "string" ||
    !anchor.quote
  )
    return false;
  try {
    const element = wnd.document.querySelector(anchor.selector);
    const node = element?.childNodes[anchor.childIndex];
    if (
      !node ||
      node.nodeType !== 3 ||
      anchor.offset >= (node as Text).length ||
      node.textContent?.slice(
        anchor.offset,
        anchor.offset + anchor.quote.length,
      ) !== anchor.quote
    )
      return false;
    const range = wnd.document.createRange();
    range.setStart(node, anchor.offset);
    range.setEnd(node, anchor.offset + 1);
    const rect = range.getBoundingClientRect();
    if (!rect.height) return false;
    const scroller = wnd.document.scrollingElement as HTMLElement | null;
    if (!scroller) return false;
    const behavior = scroller.style.getPropertyValue("scroll-behavior");
    const priority = scroller.style.getPropertyPriority("scroll-behavior");
    scroller.style.setProperty("scroll-behavior", "auto", "important");
    scroller.scrollTop += rect.top - anchor.top;
    if (behavior)
      scroller.style.setProperty("scroll-behavior", behavior, priority);
    else scroller.style.removeProperty("scroll-behavior");
    return true;
  } catch {
    return false;
  }
}

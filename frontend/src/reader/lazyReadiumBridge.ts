import type { ReadiumBridge, ReaderPreferences } from "../app/types";

let loadedBridge: ReadiumBridge | undefined;
let bridgePromise: Promise<ReadiumBridge> | undefined;

function getBridge(): Promise<ReadiumBridge> {
  if (!bridgePromise) {
    bridgePromise = import("./readiumBridge")
      .then((module) => {
        loadedBridge = module.readiumBridge;
        return loadedBridge;
      })
      .catch((error: unknown) => {
        bridgePromise = undefined;
        throw error;
      });
  }
  return bridgePromise;
}

export const readiumBridge: ReadiumBridge = {
  currentLocator: () => loadedBridge?.currentLocator() ?? null,
  goToLocator: (locator) =>
    getBridge().then((bridge) => bridge.goToLocator(locator)),
  mount: (bookId, container, onLocatorChanged, onNotice) =>
    getBridge().then((bridge) =>
      bridge.mount(bookId, container, onLocatorChanged, onNotice),
    ),
  unmount: () => getBridge().then((bridge) => bridge.unmount()),
  goToChapter: (chapterId) =>
    getBridge().then((bridge) => bridge.goToChapter(chapterId)),
  chapters: () => getBridge().then((bridge) => bridge.chapters()),
  next: () => getBridge().then((bridge) => bridge.next()),
  previous: () => getBridge().then((bridge) => bridge.previous()),
  applyPreferences: (preferences: ReaderPreferences) => {
    void getBridge()
      .then((bridge) => bridge.applyPreferences(preferences))
      .catch((error: unknown) => {
        console.error("Could not load reader rendering preferences", error);
      });
  },
};

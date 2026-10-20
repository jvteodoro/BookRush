/** One controller keeps visibility, keyboard focus and toolbar state in sync. */
export function mountReaderPanels(root: HTMLElement) {
  const entries = [
    {
      panel: root.querySelector<HTMLElement>("#tocPanel")!,
      button: root.querySelector<HTMLButtonElement>("#tocBtn")!,
    },
    {
      panel: root.querySelector<HTMLElement>("#settingsPanel")!,
      button: root.querySelector<HTMLButtonElement>("#settingsBtn")!,
    },
  ];
  let current: string | null = null;
  const setOpen = (id: string | null, restoreFocus = false) => {
    const previous = entries.find((entry) => entry.panel.id === current);
    current = id;
    for (const { panel, button } of entries) {
      const open = panel.id === id;
      panel.dataset.open = String(open);
      panel.inert = !open;
      panel.setAttribute("aria-hidden", String(!open));
      button.setAttribute("aria-expanded", String(open));
    }
    if (id)
      entries
        .find((entry) => entry.panel.id === id)
        ?.panel.querySelector<HTMLButtonElement>(".reader-panel-close")
        ?.focus();
    else if (restoreFocus) previous?.button.focus();
  };
  for (const { panel, button } of entries) {
    panel.hidden = false;
    button.setAttribute("aria-controls", panel.id);
    button.addEventListener("click", () =>
      setOpen(current === panel.id ? null : panel.id),
    );
    panel
      .querySelector<HTMLButtonElement>(".reader-panel-close")
      ?.addEventListener("click", () => setOpen(null, true));
    panel.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setOpen(null, true);
      }
    });
  }
  setOpen(null);
  return { close: () => setOpen(null, true) };
}

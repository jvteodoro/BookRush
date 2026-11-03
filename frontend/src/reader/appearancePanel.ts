import {
  defaults,
  fonts,
  normalizePreferences,
  readPreferences,
  savePreferences,
  spacing,
  themes,
  textWidths,
} from "./appearance";
import type { ReaderPreferences } from "../app/types";

/** Each reader mount owns its controls; no listeners survive the removed panel. */
export function mountAppearance(
  panel: HTMLElement,
  apply: (value: ReaderPreferences) => void,
) {
  let preferences = readPreferences();
  panel.innerHTML = `
    <p class="reader-settings-intro">Deixe a leitura do seu jeito.</p>
    <fieldset><legend>Fonte</legend><div class="reader-options reader-font-options">
      ${Object.entries(fonts)
        .map(
          ([key, font]) =>
            `<button type="button" data-font="${key}" style='font-family:${font.family}'>${font.name}</button>`,
        )
        .join("")}
    </div></fieldset>
    <label class="reader-size-label" for="readerFontSize">Tamanho da fonte <output id="readerFontSizeValue" for="readerFontSize"></output></label>
    <input id="readerFontSize" aria-describedby="readerFontSizeValue" type="range" min="85" max="170" step="5">
    <fieldset><legend>Espaçamento entre linhas</legend><div class="reader-options">
      ${spacing.map((height, index) => `<button type="button" data-spacing="${height}">${["Compacto", "Confortável", "Amplo"][index]}</button>`).join("")}
    </div></fieldset>
    <fieldset><legend>Largura do texto</legend><div class="reader-options">
      ${Object.entries(textWidths)
        .map(
          ([key, width]) =>
            `<button type="button" data-width="${key}">${width.name}</button>`,
        )
        .join("")}
    </div></fieldset>
    <fieldset><legend>Tema</legend><div class="reader-options reader-theme-options">
      ${Object.entries(themes)
        .map(
          ([key, theme]) =>
            `<button type="button" data-theme="${key}" style="background:${theme.background};color:${theme.foreground}"><span aria-hidden="true">Aa</span>${theme.name}</button>`,
        )
        .join("")}
    </div></fieldset>
    <button type="button" class="reader-reset">Restaurar padrão</button>
    <p class="reader-settings-status" role="status"></p>`;
  const groups = panel.querySelectorAll<HTMLElement>(".reader-options");
  groups.forEach((group) =>
    group.insertAdjacentHTML(
      "beforeend",
      '<span class="reader-selection-indicator" aria-hidden="true"></span>',
    ),
  );
  const size = panel.querySelector<HTMLInputElement>("#readerFontSize")!;
  const update = (persist = true) => {
    preferences = normalizePreferences(preferences);
    const saved = !persist || savePreferences(preferences);
    panel.querySelector<HTMLElement>(".reader-settings-status")!.textContent =
      saved
        ? "Preferências salvas neste navegador."
        : "Preferências aplicadas. Não foi possível salvá-las neste navegador.";
    size.value = String(preferences.fontSize);
    panel.querySelector("output")!.textContent = `${preferences.fontSize}%`;
    panel
      .querySelectorAll<HTMLButtonElement>(
        "[data-font], [data-spacing], [data-theme], [data-width]",
      )
      .forEach((button) => {
        const selected =
          button.dataset.font === preferences.fontFamily ||
          Number(button.dataset.spacing) === preferences.lineHeight ||
          button.dataset.width === preferences.textWidth ||
          (button.dataset.theme &&
            themes[button.dataset.theme as keyof typeof themes].background ===
              preferences.background);
        button.setAttribute("aria-pressed", String(Boolean(selected)));
      });
    groups.forEach((group) => {
      const buttons = Array.from(
        group.querySelectorAll<HTMLButtonElement>("button"),
      );
      const index = buttons.findIndex(
        (button) => button.getAttribute("aria-pressed") === "true",
      );
      const columns = group.classList.contains("reader-font-options") ? 2 : 3;
      group.style.setProperty(
        "--selected-column",
        String(Math.max(index, 0) % columns),
      );
      group.style.setProperty(
        "--selected-row",
        String(Math.floor(Math.max(index, 0) / columns)),
      );
    });
    const shell = panel.closest<HTMLElement>(".reader-shell");
    shell?.style.setProperty("--reader-background", preferences.background);
    shell?.style.setProperty("--reader-foreground", preferences.foreground);
    apply(preferences);
  };
  size.addEventListener("input", () => {
    preferences.fontSize = Number(size.value);
    update();
  });
  panel.addEventListener("click", (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>(
      "button",
    );
    if (!button) return;
    if (button.dataset.font)
      preferences.fontFamily = button.dataset
        .font as ReaderPreferences["fontFamily"];
    else if (button.dataset.spacing)
      preferences.lineHeight = Number(button.dataset.spacing);
    else if (button.dataset.width)
      preferences.textWidth = button.dataset
        .width as ReaderPreferences["textWidth"];
    else if (button.dataset.theme) {
      const theme = themes[button.dataset.theme as keyof typeof themes];
      preferences = {
        ...preferences,
        foreground: theme.foreground,
        background: theme.background,
      };
    } else if (button.classList.contains("reader-reset"))
      preferences = { ...defaults };
    else return;
    update();
  });
  update();
}

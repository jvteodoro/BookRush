/** Apply the same primitive styles to controls owned by the imperative adapter.
 * Keeps native nodes/listeners intact while eliminating a second visual recipe. */
export function styleNativeControls(root: HTMLElement) {
  root
    .querySelectorAll<
      HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
    >("input, select, textarea")
    .forEach((control) => {
      if (
        control instanceof HTMLInputElement &&
        ["checkbox", "radio", "range", "file", "hidden"].includes(control.type)
      )
        return;
      control.classList.add("br-input");
      if (control instanceof HTMLTextAreaElement)
        control.classList.add("br-textarea");
    });
  for (const [selector, variant] of [
    [".primary-btn", "primary"],
    [".secondary-btn", "secondary"],
    [".ghost-btn", "ghost"],
  ]) {
    root
      .querySelectorAll(selector)
      .forEach((control) =>
        control.classList.add(
          "br-button",
          `br-button--${variant}`,
          "br-button--md",
        ),
      );
  }
}

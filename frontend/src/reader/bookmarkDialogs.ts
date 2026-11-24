import { bookmarkName } from "./bookmarks";

function openDialog(
  title: string,
  initialName: string,
  deleting: boolean,
  submit: (name: string) => Promise<unknown>,
): Promise<string | true | null> {
  document.querySelector<HTMLDialogElement>(".reader-bookmark-dialog")?.close();
  const previous = document.activeElement as HTMLElement | null;
  const dialog = document.createElement("dialog");
  dialog.className = "reader-bookmark-dialog";
  dialog.setAttribute("aria-labelledby", "bookmark-dialog-title");
  dialog.innerHTML = `<form novalidate>
    <h2 id="bookmark-dialog-title"></h2>
    <p class="bookmark-dialog-description"></p>
    <label for="bookmark-name">Nome da marcação</label>
    <input id="bookmark-name" name="name" maxlength="120" autocomplete="off" required>
    <p class="bookmark-dialog-error" role="alert"></p>
    <div class="bookmark-dialog-actions"><button type="button" data-cancel>Cancelar</button><button type="submit" data-submit></button></div>
  </form>`;
  const form = dialog.querySelector("form")!;
  const input = dialog.querySelector<HTMLInputElement>("input")!;
  const label = dialog.querySelector("label")!;
  const error = dialog.querySelector<HTMLElement>("[role=alert]")!;
  const cancel = dialog.querySelector<HTMLButtonElement>("[data-cancel]")!;
  const confirm = dialog.querySelector<HTMLButtonElement>("[data-submit]")!;
  dialog.querySelector("h2")!.textContent = title;
  dialog.querySelector(".bookmark-dialog-description")!.textContent = deleting
    ? `Excluir “${initialName}”? Esta marcação será removida da sua lista.`
    : "Escolha um nome para encontrar este trecho depois.";
  input.value = initialName;
  input.hidden = label.hidden = deleting;
  input.disabled = deleting;
  confirm.textContent = deleting ? "Excluir" : "Salvar";
  confirm.className = deleting
    ? "bookmark-dialog-delete"
    : "bookmark-dialog-save";
  return new Promise((resolve) => {
    let pending = false;
    let finished = false;
    let result: string | true | null = null;
    const observer = new MutationObserver(() => {
      if (!dialog.isConnected) finish();
    });
    function finish() {
      if (finished) return;
      finished = true;
      observer.disconnect();
      dialog.remove();
      if (previous?.isConnected) previous.focus();
      resolve(result);
    }
    dialog.addEventListener("close", finish, { once: true });
    dialog.addEventListener("cancel", (event) => {
      if (pending) event.preventDefault();
    });
    cancel.addEventListener("click", () => dialog.close());
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (pending) return;
      error.textContent = "";
      let name = "";
      try {
        if (!deleting) name = bookmarkName(input.value);
      } catch (failure) {
        error.textContent = (failure as Error).message;
        input.focus();
        return;
      }
      pending = true;
      confirm.disabled = cancel.disabled = input.disabled = true;
      confirm.textContent = deleting ? "Excluindo…" : "Salvando…";
      try {
        await submit(name);
        if (finished) return;
        result = deleting ? true : name;
        dialog.close();
      } catch (failure) {
        error.textContent =
          failure instanceof Error
            ? failure.message
            : "Não foi possível concluir. Tente novamente.";
      } finally {
        pending = false;
        confirm.disabled = cancel.disabled = false;
        input.disabled = deleting;
        confirm.textContent = deleting ? "Excluir" : "Salvar";
      }
    });
    (document.getElementById("readerShell") ?? document.body).append(dialog);
    observer.observe(document.body, { childList: true, subtree: true });
    dialog.showModal();
    if (deleting) cancel.focus();
    else {
      input.focus();
      input.select();
    }
  });
}

export const bookmarkDialogs = {
  async name(
    title: string,
    initialName: string,
    submit: (name: string) => Promise<unknown>,
  ) {
    const result = await openDialog(title, initialName, false, submit);
    return typeof result === "string" ? result : null;
  },
  async remove(name: string, submit: () => Promise<unknown>) {
    return (await openDialog("Excluir marcação", name, true, submit)) === true;
  },
};

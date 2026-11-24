import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Notice } from "@bookrush/ui/Notice";
import { Button } from "../../components/ui/Button";
import {
  publicationFileType,
  publishingApi,
  type Submission,
} from "./publisherMetrics";

function message(error: unknown): string {
  const status = (error as { response?: { status?: number } })?.response
    ?.status;
  if (status === 401)
    return "Sua sessão expirou. Entre novamente para continuar.";
  if (status === 403)
    return "Sua conta não tem permissão para enviar esta publicação.";
  if (status === 404)
    return "O rascunho ou o serviço de upload não está disponível. Atualize a lista e tente novamente.";
  if (status === 409)
    return "O estado do rascunho mudou. Atualize a lista antes de tentar novamente.";
  if (status === 413) return "O arquivo excede o limite aceito pelo serviço.";
  if (status === 415) return "Escolha um livro em EPUB ou PDF.";
  if (status === 400 || status === 422)
    return "O arquivo ou os dados não puderam ser confirmados. Verifique o livro e tente novamente.";
  if (status && status >= 500)
    return "O serviço está indisponível. Seu rascunho foi preservado; tente novamente.";
  return error instanceof Error
    ? error.message
    : "Não foi possível concluir a operação.";
}

export function PublishBookForm({
  submissions,
}: {
  submissions: Submission[];
}) {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [selected, setSelected] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [rights, setRights] = useState(false);
  const [notice, setNotice] = useState("");
  const drafts = submissions.filter((book) =>
    ["DRAFT", "UPLOADED"].includes(book.status),
  );
  const draft = drafts.find((book) => book.id === selected) ?? drafts[0];
  const refresh = () =>
    void queryClient.invalidateQueries({ queryKey: ["publisher-submissions"] });
  const update = (id: string, status: string) => {
    queryClient.setQueryData<Submission[]>(["publisher-submissions"], (items) =>
      items?.map((book) => (book.id === id ? { ...book, status } : book)),
    );
    refresh();
  };
  const create = useMutation({
    mutationFn: publishingApi.create,
    onSuccess: (book) => {
      queryClient.setQueryData<Submission[]>(
        ["publisher-submissions"],
        (items) => [book, ...(items ?? [])],
      );
      setSelected(book.id);
      setRights(false);
      setTitle("");
      setFile(null);
      setNotice("Rascunho salvo. Agora anexe o arquivo do livro.");
      refresh();
    },
    onError: (failure) => setError(message(failure)),
  });
  const upload = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) =>
      publishingApi.upload(id, file),
    onSuccess: (_, { id }) => {
      update(id, "UPLOADED");
      setFile(null);
      setNotice(
        "Arquivo recebido e confirmado. Seu livro está pronto para envio à revisão.",
      );
    },
    onError: (failure) => {
      setError(message(failure));
      refresh();
    },
  });
  const submit = useMutation({
    mutationFn: async (id: string) => {
      await publishingApi.attest(id);
      return publishingApi.submit(id);
    },
    onSuccess: (result) => {
      update(result.id, result.status);
      setNotice(
        "Livro enviado para processamento e revisão. A publicação depende da aprovação editorial.",
      );
    },
    onError: (failure) => {
      setError(message(failure));
      refresh();
    },
  });
  const busy = create.isPending || upload.isPending || submit.isPending;
  const clear = () => {
    setError("");
    setNotice("");
  };
  return (
    <section
      className="publisher-publishing"
      aria-labelledby="publish-book-heading"
    >
      <h2 id="publish-book-heading">Publicar um livro</h2>
      <p>
        Salve um rascunho, anexe seu livro em EPUB ou PDF e envie para revisão
        editorial.
      </p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          clear();
          create.mutate(title);
        }}
      >
        <label htmlFor="publication-title">Título do livro</label>
        <input
          id="publication-title"
          value={title}
          maxLength={300}
          required
          disabled={busy}
          onChange={(event) => setTitle(event.target.value)}
        />
        <Button type="submit" disabled={busy || !title.trim()}>
          {create.isPending ? "Salvando…" : "Salvar rascunho"}
        </Button>
      </form>
      {draft && (
        <div className="publisher-publishing__draft">
          <label htmlFor="publication-draft">Continuar rascunho</label>
          <select
            id="publication-draft"
            value={draft.id}
            disabled={busy}
            onChange={(event) => {
              setSelected(event.target.value);
              setRights(false);
              setFile(null);
              clear();
            }}
          >
            {drafts.map((book) => (
              <option key={book.id} value={book.id}>
                {book.title} ·{" "}
                {book.status === "UPLOADED"
                  ? "Arquivo confirmado"
                  : "Aguardando arquivo"}
              </option>
            ))}
          </select>
          <label htmlFor="publication-file">
            Arquivo do livro (EPUB ou PDF)
          </label>
          <input
            key={`${draft.id}-${draft.status}`}
            id="publication-file"
            type="file"
            accept=".epub,.pdf"
            disabled={busy}
            onChange={(event) => {
              clear();
              const next = event.target.files?.[0] ?? null;
              try {
                if (next) publicationFileType(next);
                setFile(next);
              } catch (failure) {
                setFile(null);
                setError(message(failure));
              }
            }}
          />
          {draft.status === "UPLOADED" && (
            <p>
              Um arquivo já foi confirmado para este rascunho. Você pode
              enviá-lo para revisão ou substituí-lo.
            </p>
          )}
          <label>
            <input
              type="checkbox"
              checked={rights}
              disabled={busy}
              onChange={(event) => setRights(event.target.checked)}
            />
            Confirmo que tenho autorização para distribuir este livro.
          </label>
          <div className="publisher-publishing__actions">
            <Button
              disabled={busy || !file}
              onClick={() => {
                if (file) {
                  clear();
                  upload.mutate({ id: draft.id, file });
                }
              }}
            >
              {upload.isPending ? "Enviando arquivo…" : "Enviar arquivo"}
            </Button>
            <Button
              disabled={
                busy || draft.status !== "UPLOADED" || Boolean(file) || !rights
              }
              onClick={() => {
                clear();
                submit.mutate(draft.id);
              }}
            >
              {submit.isPending
                ? "Enviando para revisão…"
                : "Enviar para revisão"}
            </Button>
          </div>
          {file && draft.status === "UPLOADED" && (
            <p>Envie o novo arquivo antes de enviar o livro para revisão.</p>
          )}
        </div>
      )}
      {error && <Notice role="alert">{error}</Notice>}
      {notice && <p role="status">{notice}</p>}
      <p>
        Envie apenas obras próprias ou que você tenha autorização para
        distribuir.
      </p>
    </section>
  );
}

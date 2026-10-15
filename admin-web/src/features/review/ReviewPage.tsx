import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UseMutationResult } from "@tanstack/react-query";
import ePub from "epubjs";
import {
  CheckCircle2,
  ExternalLink,
  Image,
  RotateCcw,
  ShieldCheck,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { services, ReviewAsset, ReviewBook } from "../../lib/services";

type ReviewActionInput = {
  kind: "book" | "asset";
  bookId: string;
  assetId?: string;
};
type ReviewAction = Pick<
  UseMutationResult<void, Error, ReviewActionInput>,
  "isPending" | "mutate"
>;
type RevokeAction = Pick<
  UseMutationResult<void, Error, string>,
  "isPending" | "mutate"
>;
type AssetPreviewState = { book: ReviewBook; asset: ReviewAsset };

function assetReady(asset: ReviewBook["assets"][number]) {
  return (
    asset.status === "ACTIVE" &&
    asset.versionStatus === "AVAILABLE" &&
    asset.availabilityStatus === "AVAILABLE"
  );
}

function formatDate(value?: string) {
  if (!value) return "data desconhecida";
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

export function ReviewPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<"queue" | "history">("queue");
  const [page, setPage] = useState(0);
  const [previewAsset, setPreviewAsset] = useState<AssetPreviewState | null>(
    null,
  );
  const books = useQuery({
    queryKey: ["review-books", page],
    queryFn: () => services.listReviewBooks(page),
    enabled: tab === "queue",
  });
  const history = useQuery({
    queryKey: ["approval-history", page],
    queryFn: () => services.listApprovalHistory(page),
    enabled: tab === "history",
  });
  const action = useMutation({
    mutationFn: ({
      kind,
      bookId,
      assetId,
    }: {
      kind: "book" | "asset";
      bookId: string;
      assetId?: string;
    }) =>
      kind === "book"
        ? services.approveBook(bookId)
        : services.approveAsset(bookId, assetId!),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["review-books"] });
      void qc.invalidateQueries({ queryKey: ["approval-history"] });
    },
  });
  const revoke = useMutation({
    mutationFn: services.revokeBookApproval,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["approval-history"] });
      void qc.invalidateQueries({ queryKey: ["review-books"] });
    },
  });
  function openAsset(book: ReviewBook, asset: ReviewAsset) {
    setPreviewAsset({ book, asset });
  }
  const error = action.error ?? revoke.error;
  return (
    <section>
      <header className="pagehead">
        <div>
          <span className="eyebrow">CONTENT GOVERNANCE</span>
          <h1>Review Queue</h1>
          <p>
            Inspecione os arquivos antes de aprovar livros e thumbnails. Toda
            decisão fica registrada e uma aprovação pode ser revogada.
          </p>
        </div>
        <span className="sessionbadge">
          {tab === "queue"
            ? `${books.data?.totalItems ?? 0} aguardando`
            : `${history.data?.totalItems ?? 0} decisões`}
        </span>
      </header>
      <div
        className="review-tabs"
        role="tablist"
        aria-label="Revisão editorial"
      >
        <button
          className={tab === "queue" ? "active" : ""}
          onClick={() => {
            setTab("queue");
            setPage(0);
          }}
        >
          Fila de revisão
        </button>
        <button
          className={tab === "history" ? "active" : ""}
          onClick={() => {
            setTab("history");
            setPage(0);
          }}
        >
          Histórico de aprovações
        </button>
      </div>
      {error && (
        <div className="error-box" role="alert">
          {error instanceof Error ? error.message : "A operação foi rejeitada."}
        </div>
      )}
      {tab === "queue" ? (
        <QueueContent
          books={books.data?.items ?? []}
          loading={books.isLoading}
          error={books.error}
          action={action}
          openAsset={openAsset}
        />
      ) : (
        <HistoryContent
          history={history.data?.items ?? []}
          loading={history.isLoading}
          error={history.error}
          revoke={revoke}
        />
      )}
      <div className="toolbar">
        <button
          disabled={page === 0}
          onClick={() => setPage((current) => current - 1)}
        >
          Página anterior
        </button>
        <button
          disabled={
            (tab === "queue" ? books.data?.last : history.data?.last) ?? true
          }
          onClick={() => setPage((current) => current + 1)}
        >
          Próxima página
        </button>
      </div>
      {previewAsset && (
        <AssetPreview
          preview={previewAsset}
          onClose={() => setPreviewAsset(null)}
        />
      )}
    </section>
  );
}

function QueueContent({
  books,
  loading,
  error,
  action,
  openAsset,
}: {
  books: ReviewBook[];
  loading: boolean;
  error: unknown;
  action: ReviewAction;
  openAsset: (book: ReviewBook, asset: ReviewAsset) => void;
}) {
  if (loading)
    return (
      <section className="panel">
        <h2>Carregando fila</h2>
        <p>Buscando livros e assets aguardando revisão…</p>
      </section>
    );
  if (error)
    return (
      <section className="panel">
        <h2>Fila indisponível</h2>
        <p>
          {error instanceof Error
            ? error.message
            : "Não foi possível carregar a fila de revisão."}
        </p>
      </section>
    );
  if (books.length === 0)
    return (
      <div className="panel">
        <h2>Nenhum item aguardando revisão</h2>
        <p>Novas importações com status REVIEW_REQUIRED aparecerão aqui.</p>
      </div>
    );
  return (
    <div className="review-list">
      {books.map((book) => (
        <article className="review-card" key={book.id}>
          <div className="review-card-head">
            <div>
              <span className="source">GUTENBERG / REVIEW_REQUIRED</span>
              <h2>
                {book.canonicalTitle ?? book.originalTitle ?? "Obra sem título"}
              </h2>
              <p>
                {book.originalTitle &&
                book.originalTitle !== book.canonicalTitle
                  ? book.originalTitle
                  : ""}{" "}
                {book.originalLanguage
                  ? `· ${book.originalLanguage.toUpperCase()}`
                  : ""}{" "}
                {book.firstPublicationYear
                  ? `· ${book.firstPublicationYear}`
                  : ""}
              </p>
            </div>
            <button
              className="primary-action"
              disabled={action.isPending}
              onClick={() => action.mutate({ kind: "book", bookId: book.id })}
            >
              <ShieldCheck size={16} />
              Aprovar livro
            </button>
          </div>
          {book.description && (
            <p className="review-description">{book.description}</p>
          )}
          <div className="review-assets">
            <strong>Assets associados</strong>
            {book.assets.length === 0 ? (
              <p>Nenhum asset encontrado.</p>
            ) : (
              book.assets.map((asset) => (
                <div className="review-asset" key={asset.id}>
                  <div>
                    <Image size={17} />
                    <span>
                      <b>
                        {asset.assetType} · {asset.assetRole}
                      </b>
                      <small>
                        {asset.contentType ?? "tipo desconhecido"} ·{" "}
                        {asset.sizeBytes
                          ? `${Math.round(asset.sizeBytes / 1024)} KB`
                          : "tamanho desconhecido"}{" "}
                        · {asset.versionStatus ?? "sem versão"}
                      </small>
                    </span>
                  </div>
                  <div className="review-asset-actions">
                    {asset.versionId && (
                      <button
                        className="secondary-action"
                        onClick={() => openAsset(book, asset)}
                      >
                        <ExternalLink size={15} />
                        Abrir para revisão
                      </button>
                    )}
                    {assetReady(asset) ? (
                      <span className="review-approved">
                        <CheckCircle2 size={16} />
                        Aprovado
                      </span>
                    ) : (
                      <button
                        className="secondary-action"
                        disabled={action.isPending}
                        onClick={() =>
                          action.mutate({
                            kind: "asset",
                            bookId: book.id,
                            assetId: asset.id,
                          })
                        }
                      >
                        <CheckCircle2 size={16} />
                        Aprovar asset
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </article>
      ))}
    </div>
  );
}

function AssetPreview({
  preview,
  onClose,
}: {
  preview: AssetPreviewState;
  onClose: () => void;
}) {
  const link = useQuery({
    queryKey: ["review-asset-preview", preview.book.id, preview.asset.id],
    queryFn: () =>
      services.getAssetDownloadUrl(preview.book.id, preview.asset.id),
  });
  const type = (
    preview.asset.assetType ||
    preview.asset.contentType ||
    ""
  ).toUpperCase();
  const contentType = (preview.asset.contentType ?? "").toLowerCase();
  const textAsset =
    type === "TXT" ||
    type === "JSON" ||
    contentType.startsWith("text/") ||
    contentType.includes("json");
  const imageAsset =
    type === "THUMBNAIL" ||
    type === "COVER" ||
    contentType.startsWith("image/");
  const epubAsset = type === "EPUB" || contentType.includes("epub");
  return (
    <div
      className="asset-preview-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="asset-preview"
        role="dialog"
        aria-modal="true"
        aria-labelledby="asset-preview-title"
      >
        <header className="asset-preview-head">
          <div>
            <span className="source">VISUALIZAÇÃO DE ASSET</span>
            <h2 id="asset-preview-title">
              {preview.book.canonicalTitle ??
                preview.book.originalTitle ??
                "Obra sem título"}
            </h2>
            <p>
              {preview.asset.assetType} ·{" "}
              {preview.asset.contentType ?? "tipo desconhecido"}
            </p>
          </div>
          <button className="secondary-action" onClick={onClose}>
            Fechar
          </button>
        </header>
        {link.isLoading && (
          <div className="asset-preview-state">Preparando visualização…</div>
        )}
        {link.isError && (
          <div className="asset-preview-state error-text">
            {link.error instanceof Error
              ? link.error.message
              : "Não foi possível abrir este asset."}
          </div>
        )}
        {link.data && imageAsset && (
          <div className="asset-preview-image">
            <img
              src={link.data.url}
              alt={`Pré-visualização de ${preview.asset.assetType}`}
            />
          </div>
        )}
        {link.data && textAsset && (
          <TextAsset
            url={link.data.url}
            json={type === "JSON" || contentType.includes("json")}
          />
        )}
        {link.data && epubAsset && <EpubAsset url={link.data.url} />}
        {link.data && !imageAsset && !textAsset && !epubAsset && (
          <div className="asset-preview-state">
            Formato não suportado para leitura integrada.{" "}
            <a href={link.data.url} target="_blank" rel="noreferrer">
              Abrir o arquivo
            </a>
          </div>
        )}
      </section>
    </div>
  );
}

function TextAsset({ url, json }: { url: string; json: boolean }) {
  const text = useQuery({
    queryKey: ["review-asset-text", url],
    queryFn: async () => {
      const response = await fetch(url);
      if (!response.ok)
        throw new Error(`Download do asset falhou (HTTP ${response.status}).`);
      return response.text();
    },
  });
  let content = text.data;
  if (content && json) {
    try {
      content = JSON.stringify(JSON.parse(content), null, 2);
    } catch {
      /* show malformed JSON as received */
    }
  }
  return text.isLoading ? (
    <div className="asset-preview-state">Carregando conteúdo…</div>
  ) : text.isError ? (
    <div className="asset-preview-state error-text">
      {text.error instanceof Error
        ? text.error.message
        : "Não foi possível carregar o texto."}
    </div>
  ) : (
    <pre className="asset-preview-text">{content}</pre>
  );
}

function EpubAsset({ url }: { url: string }) {
  const container = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!container.current) return;
    const book = ePub(url);
    const rendition = book.renderTo(container.current, {
      width: "100%",
      height: "100%",
    });
    void rendition
      .display()
      .catch(() =>
        setError(
          "Não foi possível renderizar este EPUB. Verifique se o arquivo está íntegro e se o storage permite leitura pelo admin-web.",
        ),
      );
    return () => {
      rendition.destroy();
      book.destroy();
    };
  }, [url]);
  return error ? (
    <div className="asset-preview-state error-text">{error}</div>
  ) : (
    <div className="asset-preview-epub" ref={container} />
  );
}

function HistoryContent({
  history,
  loading,
  error,
  revoke,
}: {
  history: Array<{
    id: string;
    bookId: string;
    canonicalTitle?: string;
    originalTitle?: string;
    action: string;
    previousStatus: string;
    newStatus: string;
    actorSubject: string;
    createdAt: string;
    currentStatus: string;
  }>;
  loading: boolean;
  error: unknown;
  revoke: RevokeAction;
}) {
  if (loading)
    return (
      <section className="panel">
        <h2>Carregando histórico</h2>
        <p>Buscando decisões editoriais…</p>
      </section>
    );
  if (error)
    return (
      <section className="panel">
        <h2>Histórico indisponível</h2>
        <p>
          {error instanceof Error
            ? error.message
            : "Não foi possível carregar o histórico."}
        </p>
      </section>
    );
  if (history.length === 0)
    return (
      <div className="panel">
        <h2>Nenhuma decisão registrada</h2>
        <p>Aprovações e revogações aparecerão aqui.</p>
      </div>
    );
  return (
    <div className="review-history">
      {history.map((item) => (
        <article className="history-row" key={item.id}>
          <div>
            <span className={`history-action ${item.action.toLowerCase()}`}>
              {item.action === "APPROVED" ? "Aprovado" : "Revogado"}
            </span>
            <h2>
              {item.canonicalTitle ?? item.originalTitle ?? "Obra sem título"}
            </h2>
            <p>
              {formatDate(item.createdAt)} · {item.actorSubject} ·{" "}
              {item.previousStatus} → {item.newStatus}
            </p>
          </div>
          {item.action === "APPROVED" && item.currentStatus === "ACTIVE" && (
            <button
              className="secondary-action"
              disabled={revoke.isPending}
              onClick={() => revoke.mutate(item.bookId)}
            >
              <RotateCcw size={15} />
              Revogar aprovação
            </button>
          )}
        </article>
      ))}
    </div>
  );
}

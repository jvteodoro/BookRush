import { useSearchParams } from "react-router-dom";
import { SearchField } from "@bookrush/ui/SearchField";
import { Surface } from "@bookrush/ui/Surface";
import { PageHeader } from "@bookrush/ui/PageHeader";
import { Button } from "../../components/ui/Button";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen, Download, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ImportJob, services } from "../../lib/services";

const terminalStatuses = [
  "COMPLETED",
  "COMPLETED_WITH_ERRORS",
  "FAILED",
  "CANCELLED",
];
const statusLabels: Record<string, string> = {
  PENDING: "Na fila",
  RUNNING: "Processando",
  COMPLETED: "Concluída",
  COMPLETED_WITH_ERRORS: "Concluída com erros",
  FAILED: "Falhou",
  CANCELLED: "Cancelada",
};

export function CatalogPage() {
  const qc = useQueryClient();
  const [searchParams] = useSearchParams();
  const [q, setQ] = useState(searchParams.get("q") ?? "");
  useEffect(() => {
    setQ(searchParams.get("q") ?? "");
    setPage(0);
  }, [searchParams]);
  const [page, setPage] = useState(0);
  const [job, setJob] = useState<ImportJob>();
  const [jobBook, setJobBook] = useState<string>();
  const books = useQuery({
    queryKey: ["books", q, page],
    queryFn: () => services.listBookPage(q, page, 50),
  });
  const imp = useMutation({
    mutationFn: ({
      bookId,
      reprocess,
    }: {
      bookId: string;
      reprocess: boolean;
    }) => services.importBook(bookId, reprocess),
    onMutate: ({ bookId }) => {
      setJobBook(bookId);
      setJob(undefined);
    },
    onSuccess: (result) => {
      setJob(result);
      void qc.invalidateQueries({ queryKey: ["books"] });
    },
  });
  useEffect(() => {
    if (!job?.id || terminalStatuses.includes(job.status)) return;
    const timer = window.setInterval(
      () =>
        void services
          .getImportJob(job.id!)
          .then((next) => {
            setJob(next);
            if (terminalStatuses.includes(next.status))
              void qc.invalidateQueries({ queryKey: ["books"] });
          })
          .catch((error) =>
            setJob((current) => ({
              ...current,
              status: "FAILED",
              errorMessage:
                error instanceof Error
                  ? error.message
                  : "Não foi possível atualizar o status.",
            })),
          ),
      2000,
    );
    return () => window.clearInterval(timer);
  }, [job, qc]);
  const filtered = useMemo(
    () =>
      books.data?.items.filter((book) =>
        `${book.title} ${book.author}`.toLowerCase().includes(q.toLowerCase()),
      ) ?? [],
    [books.data, q],
  );
  if (books.isLoading)
    return (
      <Surface as="section" className="panel">
        <h1>Catálogo Gutenberg</h1>
        <p>Consultando o índice paginado…</p>
      </Surface>
    );
  if (books.isError)
    return (
      <Surface as="section" className="panel">
        <h1>Catálogo indisponível</h1>
        <p>
          {books.error instanceof Error
            ? books.error.message
            : "Não foi possível consultar o catálogo Gutenberg."}
        </p>
        <p>
          Faça login com uma conta do realm administrativo que tenha OPERATOR,
          REVIEWER ou CLEANUP.
        </p>
      </Surface>
    );
  const progress = job?.itemsDiscovered
    ? Math.min(
        100,
        Math.round(((job.itemsProcessed ?? 0) / job.itemsDiscovered) * 100),
      )
    : 0;
  const terminal = !job || terminalStatuses.includes(job.status);
  return (
    <section className="catalog-page">
      <PageHeader className="">
        <div>
          <span className="eyebrow">CONTENT OPERATIONS</span>
          <h1>Catalog & Import</h1>
          <p>
            Índice paginado do Gutenberg; escolha uma obra, confira a
            proveniência e envie o ID para a pipeline.
          </p>
        </div>
      </PageHeader>
      <div className="catalog-toolbar">
        <SearchField
          value={q}
          onChange={(event) => {
            setQ(event.target.value);
            setPage(0);
          }}
          placeholder="Buscar título ou autor"
          aria-label="Buscar título ou autor"
        />
        <span>
          {filtered.length} obras nesta página · {books.data?.totalItems ?? "—"}{" "}
          no índice · página {page + 1}
        </span>
      </div>
      {job && (
        <section
          className={`panel import-status ${terminal && job.status !== "COMPLETED" ? "import-error" : ""}`}
        >
          <div className="panelhead">
            <div>
              <h2>Importação {jobBook ? `do Gutenberg #${jobBook}` : ""}</h2>
              <span>{statusLabels[job.status] ?? job.status}</span>
            </div>
            <strong>{progress}%</strong>
          </div>
          <div className="progress">
            <span style={{ width: `${progress}%` }} />
          </div>
          <div className="import-stats">
            <span>
              Descobertos <b>{job.itemsDiscovered ?? 0}</b>
            </span>
            <span>
              Processados <b>{job.itemsProcessed ?? 0}</b>
            </span>
            <span>
              Sucesso <b>{job.itemsSucceeded ?? 0}</b>
            </span>
            <span>
              Falhas <b>{job.itemsFailed ?? 0}</b>
            </span>
          </div>
          {imp.isError && !job.errorMessage && (
            <p className="error-text">
              {imp.error instanceof Error
                ? imp.error.message
                : "A importação foi rejeitada."}
            </p>
          )}
          {job.errorMessage && <p className="error-text">{job.errorMessage}</p>}
          {!terminal && (
            <small>Atualização automática a cada 2 segundos.</small>
          )}
          {terminal && job.status === "COMPLETED" && (
            <p className="success-text">
              Importação concluída. O status persistido do catálogo foi
              atualizado.
            </p>
          )}
        </section>
      )}
      {filtered.length === 0 ? (
        <Surface as="div" className="panel">
          <h2>Nenhuma obra nesta página</h2>
          <p>Use outra busca ou avance pelas páginas do índice Gutenberg.</p>
        </Surface>
      ) : (
        <div className="catalog-grid">
          {filtered.map((book) => (
            <article className="br-card catalog-card" key={book.id}>
              <div className="catalog-card-heading">
                <div className="catalog-cover" aria-hidden="true">
                  <BookOpen size={28} strokeWidth={1.5} />
                  <span>{book.language.toUpperCase()}</span>
                </div>
                <div className="catalog-copy">
                  <span className="catalog-source">{book.source}</span>
                  <h3>{book.title}</h3>
                  <p className="catalog-author">{book.author}</p>
                </div>
              </div>
              <div className="catalog-meta">
                <span>{book.language.toUpperCase()}</span>
                <span>
                  {book.status === "IMPORTED"
                    ? "IMPORTADO"
                    : book.status === "IMPORTING"
                      ? "PROCESSANDO"
                      : book.status === "QUEUED"
                        ? "NA FILA"
                        : "DISPONÍVEL"}
                </span>
              </div>
              <p className="catalog-description">
                {book.description ??
                  "Metadata e proveniência serão exibidos pelo catálogo."}
              </p>
              <footer className="catalog-card-footer">
                <Button
                  variant="primary"
                  size="sm"
                  fullWidth
                  disabled={
                    book.status === "QUEUED" ||
                    book.status === "IMPORTING" ||
                    imp.isPending
                  }
                  onClick={() =>
                    imp.mutate({
                      bookId: book.id,
                      reprocess: book.status === "IMPORTED",
                    })
                  }
                >
                  {book.status === "IMPORTED" ? (
                    <>
                      <RefreshCw size={16} />
                      Reimportar assets
                    </>
                  ) : book.status === "IMPORTING" ? (
                    <>Processando…</>
                  ) : book.status === "QUEUED" ? (
                    <>Na fila…</>
                  ) : (
                    <>
                      <Download size={16} />
                      Importar livro
                    </>
                  )}
                </Button>
              </footer>
            </article>
          ))}
        </div>
      )}
      <div className="catalog-pagination">
        <Button
          variant="ghost"
          disabled={page === 0}
          onClick={() => setPage((current) => current - 1)}
        >
          Página anterior
        </Button>
        <Button
          variant="ghost"
          disabled={!books.data?.hasNext}
          onClick={() => setPage((current) => current + 1)}
        >
          Próxima página
        </Button>
      </div>
    </section>
  );
}

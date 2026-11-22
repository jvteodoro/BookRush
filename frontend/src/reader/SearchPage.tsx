import { Tabs } from "@bookrush/ui/Tabs";
import { Notice } from "@bookrush/ui/Notice";
import { SearchField } from "@bookrush/ui/SearchField";
import { PageHeader } from "@bookrush/ui/PageHeader";
import { Toolbar } from "@bookrush/ui/Toolbar";
import { Input } from "@bookrush/ui/Input";
import { useSeenBooks } from "./useSeenBooks";
import { useEffect, useState } from "react";
import { BookCard } from "../components/book/BookCard";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { Icon } from "../components/ui/Icon";
import { CarouselControl } from "../components/ui/CarouselControl";
import type { LibraryBook } from "./library";
import { UserSearchResults } from "./UserSearchResults";
import { searchCatalog, type SearchResult } from "./search";

type Props = {
  onDetails?: (book: LibraryBook) => void;
  visible: boolean;
  query: string;
  onQuery: (value: string) => void;
  saved: string[];
  pending: string[];
  message: string;
  onSave: (id: string) => void;
  onOpen: (book: LibraryBook) => void;
  onProfile: (subject: string) => void;
};
export function SearchPage({
  visible,
  query,
  onQuery,
  saved,
  pending,
  message,
  onSave,
  onOpen,
  onProfile,
  onDetails,
}: Props) {
  const [kind, setKind] = useState<"books" | "people">("books");
  const [category, setCategory] = useState("");
  const [author, setAuthor] = useState("");
  const [year, setYear] = useState("");
  const clearFilters = () => {
    setCategory("");
    setAuthor("");
    setYear("");
    setPage(0);
  };
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [data, setData] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    setPage(0);
  }, [query, category, author, year]);
  useEffect(() => {
    if (!visible || kind !== "books") return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const timer = window.setTimeout(
      () => {
        void searchCatalog(query, page, controller.signal, undefined, {
          category,
          author,
          year,
        })
          .then((result) => {
            if (!controller.signal.aborted) setData(result);
          })
          .catch((error) => {
            if (!controller.signal.aborted) setError(error.message);
          })
          .finally(() => {
            if (!controller.signal.aborted) setLoading(false);
          });
      },
      query.trim() || category || author || year ? 300 : 0,
    );
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, page, attempt, visible, kind, category, author, year]);
  const seenState = useSeenBooks(
    data?.books.map((book) => book.id) ?? [],
    visible && kind === "books",
  );
  return (
    <section aria-busy={kind === "books" && loading}>
      <PageHeader>
        <div>
          <h1>Encontre sua próxima história</h1>
          <p className="br-muted">
            Busque livros por título, autor, ano, descrição ou categoria e
            pessoas por nome ou @usuário.
          </p>
        </div>
      </PageHeader>
      <form
        className="br-search-form"
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          setAttempt((current) => current + 1);
        }}
      >
        <SearchField
          id="catalog-search"
          label="Buscar livros ou pessoas"
          value={query}
          placeholder={
            kind === "people"
              ? "Um nome ou @usuário…"
              : "Título, autor, ano ou categoria…"
          }
          maxLength={kind === "people" ? 100 : 500}
          onChange={(event) => onQuery(event.target.value)}
          action={
            query ? (
              <button
                type="button"
                aria-label="Limpar busca"
                onClick={() => onQuery("")}
              >
                <Icon name="close" />
              </button>
            ) : undefined
          }
        />
        {kind === "books" && (
          <fieldset className="br-search-filters">
            <legend>Filtrar livros</legend>
            <label htmlFor="search-category">
              Categoria
              <Input
                id="search-category"
                value={category}
                maxLength={200}
                placeholder="Ex.: Adventure"
                onChange={(event) => setCategory(event.target.value)}
              />
            </label>
            <label htmlFor="search-author">
              Autor
              <Input
                id="search-author"
                value={author}
                maxLength={200}
                placeholder="Ex.: Machado de Assis"
                onChange={(event) => setAuthor(event.target.value)}
              />
            </label>
            <label htmlFor="search-year">
              Ano de publicação
              <Input
                id="search-year"
                type="number"
                min={-9999}
                max={9999}
                step={1}
                value={year}
                placeholder="Ex.: 1899"
                onChange={(event) => setYear(event.target.value)}
              />
            </label>
            <Button
              type="button"
              variant="ghost"
              disabled={!category && !author && !year}
              onClick={clearFilters}
            >
              Limpar filtros
            </Button>
          </fieldset>
        )}
      </form>
      <Tabs role="group" aria-label="Tipo de busca">
        <Button
          variant={kind === "books" ? "primary" : "ghost"}
          aria-pressed={kind === "books"}
          onClick={() => setKind("books")}
        >
          Livros
        </Button>
        <Button
          variant={kind === "people" ? "primary" : "ghost"}
          aria-pressed={kind === "people"}
          onClick={() => setKind("people")}
        >
          Pessoas
        </Button>
      </Tabs>
      {kind === "people" ? (
        <UserSearchResults
          key={query}
          visible={visible}
          query={query}
          onProfile={onProfile}
          requestVersion={attempt}
        />
      ) : loading ? (
        <p role="status">Buscando livros…</p>
      ) : error ? (
        <Card className="br-live-empty">
          <Notice as="p" role="alert">
            {error}
          </Notice>
          <Button onClick={() => setAttempt((current) => current + 1)}>
            Tentar novamente
          </Button>
        </Card>
      ) : !data?.books.length ? (
        <Card className="br-live-empty">
          <h2>Nenhum livro encontrado</h2>
          <p>Tente outro título, metadado ou combinação de filtros.</p>
          <Button
            variant="ghost"
            onClick={() => {
              onQuery("");
              clearFilters();
            }}
          >
            Explorar catálogo
          </Button>
        </Card>
      ) : (
        <>
          <Toolbar className="br-search-summary">
            <p role="status">
              {data.totalItems}{" "}
              {data.totalItems === 1
                ? "livro encontrado"
                : "livros encontrados"}
            </p>
            <span>
              {query.trim() ? "Relevância e título" : "Ordenados por título"}
            </span>
          </Toolbar>
          <div className="br-search-results">
            {data.books.map((book) => (
              <Card className="br-search-result" key={book.id}>
                <div className="br-search-result__cover">
                  <BookCard
                    book={{ ...book, seen: seenState.seen.has(book.id) }}
                    onOpen={() => (onDetails ?? onOpen)(book)}
                    disabled={pending.includes(book.id)}
                  />
                </div>
                <div className="br-search-result__copy">
                  <h2>
                    <button
                      type="button"
                      className="br-search-result__open"
                      disabled={pending.includes(book.id)}
                      aria-label={`Ver ${book.title}, de ${book.author}`}
                      onClick={() => (onDetails ?? onOpen)(book)}
                    >
                      {book.title}
                    </button>
                  </h2>
                  <p>
                    {book.author}
                    {book.publicationYear != null
                      ? ` · ${book.publicationYear}`
                      : ""}{" "}
                    · {book.genre}
                  </p>
                  {book.description && (
                    <p className="br-search-result__description">
                      {book.description}
                    </p>
                  )}
                </div>
                <div className="br-search-result__actions">
                  <Button
                    variant={saved.includes(book.id) ? "secondary" : "ghost"}
                    size="sm"
                    disabled={pending.includes(book.id)}
                    aria-pressed={saved.includes(book.id)}
                    aria-label={`${saved.includes(book.id) ? "Remover" : "Salvar"} ${book.title} na biblioteca`}
                    onClick={() => onSave(book.id)}
                  >
                    <Icon name="bookmark" />
                    {saved.includes(book.id) ? "Salvo" : "Salvar"}
                  </Button>
                  <Button
                    size="sm"
                    disabled={pending.includes(book.id)}
                    onClick={() => onOpen(book)}
                  >
                    Ler agora
                  </Button>
                </div>
              </Card>
            ))}
          </div>
          {data.totalPages > 1 && (
            <nav
              className="br-search-pagination"
              aria-label="Páginas de resultados"
            >
              <CarouselControl
                direction="previous"
                label="Página anterior"
                disabled={page === 0}
                onClick={() => setPage((current) => current - 1)}
              />
              <span>
                Página {data.page + 1} de {data.totalPages}
              </span>
              <CarouselControl
                direction="next"
                label="Próxima página"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
              />
            </nav>
          )}
        </>
      )}
      {seenState.error && (
        <p role="status" className="br-seen-warning">
          {seenState.error}{" "}
          <Button variant="ghost" size="sm" onClick={seenState.retry}>
            Tentar novamente
          </Button>
        </p>
      )}
      <p role="status" className="br-feed-message">
        {message}
      </p>
    </section>
  );
}

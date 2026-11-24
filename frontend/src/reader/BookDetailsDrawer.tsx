import { Notice } from "@bookrush/ui/Notice";
import { categoryNames, type BookCategory } from "./bookCategories";
import { useSeenBooks } from "./useSeenBooks";
import { SeenBadge } from "../components/book/SeenBadge";
import { useEffect, useRef, useState } from "react";
import { Drawer } from "../components/ui/Drawer";
import { Button } from "../components/ui/Button";
import { Icon } from "../components/ui/Icon";
import { AuthenticatedImage } from "../components/book/AuthenticatedImage";
import type { FeedExcerpt } from "../app/types";
import {
  loadRatings,
  saveRating,
  removeRating,
  ratingLabel,
  type RatingSummary,
} from "./ratings";

export type DetailsBook = {
  id: string;
  title: string;
  author?: string;
  description?: string;
  categories?: BookCategory[];
  language?: string | null;
  genre?: string;
  coverUrl?: string;
  thumbnailUrl?: string | null;
  progress?: number;
  excerpt?: FeedExcerpt | null;
};
export function BookDetailsDrawer({
  book,
  onClose,
  onRead,
  onSave,
  onComments,
  saved = false,
  pending = false,
  actionMessage = "",
}: {
  book: DetailsBook;
  onClose: () => void;
  onRead?: () => void;
  onSave?: () => void;
  onComments?: () => void;
  saved?: boolean;
  pending?: boolean;
  actionMessage?: string;
}) {
  const [summary, setSummary] = useState<RatingSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState(0);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const [failedCover, setFailedCover] = useState<string>();
  const lock = useRef(false);
  const seenState = useSeenBooks([book.id]);
  const categories = categoryNames(book.categories);
  const cover = book.coverUrl || book.thumbnailUrl;
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    void loadRatings(book.id, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setSummary(data);
        setSelected(data.myRating ?? 0);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Não foi possível carregar as avaliações.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [book.id, attempt]);
  const submit = async (remove = false) => {
    if (lock.current || (!remove && !selected)) return;
    lock.current = true;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const data = remove
        ? await removeRating(book.id)
        : await saveRating(book.id, selected);
      setSummary(data);
      setSelected(data.myRating ?? 0);
      setNotice(remove ? "Avaliação removida." : "Avaliação salva.");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Não foi possível salvar a avaliação.",
      );
    } finally {
      lock.current = false;
      setSaving(false);
    }
  };
  return (
    <Drawer title={book.title} className="br-book-modal" onClose={onClose}>
      <div className="br-book-modal__hero">
        <span className="br-book-modal__watermark" aria-hidden="true">
          B
        </span>
        <div className="br-book-modal__cover">
          {cover && failedCover !== cover ? (
            <AuthenticatedImage
              src={cover}
              alt={`Capa de ${book.title}`}
              onError={() => setFailedCover(cover)}
            />
          ) : (
            <div className="br-book-modal__cover-fallback">
              <small>BOOKRUSH</small>
              <strong>{book.title}</strong>
              <span>{book.author}</span>
            </div>
          )}
        </div>
        <div className="br-book-modal__intro">
          <span className="br-book-modal__eyebrow">SUA PRÓXIMA HISTÓRIA</span>
          <h3>{book.title}</h3>
          {book.author && (
            <p className="br-book-modal__author">de {book.author}</p>
          )}
          <div className="br-book-modal__tags">
            {(categories.length
              ? categories
              : book.genre
                ? [book.genre]
                : []
            ).map((name) => (
              <span key={name}>{name}</span>
            ))}
            {book.language && <span>{book.language.toUpperCase()}</span>}
          </div>
          <div className="br-book-modal__actions">
            {onRead && (
              <Button disabled={pending} onClick={onRead}>
                <Icon name="library" />
                {book.progress && book.progress > 0
                  ? "Continuar leitura"
                  : "Começar a ler"}
              </Button>
            )}
            {onSave && (
              <Button
                variant="ghost"
                disabled={pending}
                aria-pressed={saved}
                onClick={onSave}
              >
                <Icon name="bookmark" />
                {saved ? "Na sua biblioteca" : "Salvar na biblioteca"}
              </Button>
            )}
          </div>
          <SeenBadge seen={seenState.seen.has(book.id)} />
        </div>
      </div>
      <div className="br-book-modal__content">
        {actionMessage && <p role="status">{actionMessage}</p>}
        <div className="br-book-modal__overview">
          <section
            className="br-book-modal__synopsis"
            aria-label="Sobre o livro"
          >
            <span className="br-book-modal__eyebrow">ENTRE NESSA HISTÓRIA</span>
            <h3>Sobre o livro</h3>
            <p className="br-book-description">
              {book.description ||
                "A sinopse deste livro ainda não está disponível."}
            </p>
            {!book.description && book.excerpt?.text && (
              <blockquote>
                <p>{book.excerpt.text}</p>
                <cite>Um trecho para conhecer a história</cite>
              </blockquote>
            )}
          </section>
          <aside
            className="br-book-modal__facts"
            aria-label="Informações do livro"
          >
            <h3>Em detalhes</h3>
            <dl>
              {book.author && (
                <>
                  <dt>Autor</dt>
                  <dd>{book.author}</dd>
                </>
              )}
              <dt>Categorias</dt>
              <dd>
                {categories.join(" · ") || book.genre || "Não informadas"}
              </dd>
              <dt>Idioma</dt>
              <dd>{book.language?.toUpperCase() || "Não informado"}</dd>
              {typeof book.progress === "number" && book.progress > 0 && (
                <>
                  <dt>Sua leitura</dt>
                  <dd>{Math.round(book.progress)}% concluído</dd>
                </>
              )}
            </dl>
          </aside>
        </div>
        {seenState.error && (
          <p role="status">
            {seenState.error}{" "}
            <Button variant="ghost" size="sm" onClick={seenState.retry}>
              Tentar novamente
            </Button>
          </p>
        )}
        <section
          className="br-book-modal__ratings"
          aria-label="Avaliações do livro"
          aria-busy={loading || saving}
        >
          <span className="br-book-modal__eyebrow">
            A HISTÓRIA CONTINUA COM VOCÊ
          </span>
          <h3>Avaliações dos leitores</h3>
          {loading ? (
            <p role="status">Carregando avaliações…</p>
          ) : (
            <>
              {summary && (
                <div className="br-book-modal__rating-summary">
                  <strong>
                    {summary.totalRatings && summary.averageRating != null
                      ? new Intl.NumberFormat("pt-BR", {
                          maximumFractionDigits: 1,
                        }).format(summary.averageRating)
                      : "—"}
                    <span aria-hidden="true"> ★</span>
                  </strong>
                  <p role="status">{ratingLabel(summary)}</p>
                </div>
              )}
              {error && (
                <Notice as="p" role="alert">
                  {error}
                </Notice>
              )}
              {!summary ? (
                <Button onClick={() => setAttempt((value) => value + 1)}>
                  Tentar novamente
                </Button>
              ) : (
                <>
                  <p>
                    {summary.myRating
                      ? `Sua avaliação atual: ${summary.myRating} de 5 estrelas.`
                      : "Você ainda não avaliou este livro."}
                  </p>
                  <fieldset className="br-rating-stars" disabled={saving}>
                    <legend>Sua avaliação</legend>
                    {[1, 2, 3, 4, 5].map((stars) => (
                      <label
                        key={stars}
                        title={`${stars} ${stars === 1 ? "estrela" : "estrelas"}`}
                      >
                        <input
                          type="radio"
                          name={`rating-${book.id}`}
                          value={stars}
                          checked={selected === stars}
                          onChange={() => setSelected(stars)}
                          aria-label={`${stars} ${stars === 1 ? "estrela" : "estrelas"}`}
                        />
                        <span
                          aria-hidden="true"
                          data-filled={stars <= selected}
                        >
                          ★
                        </span>
                      </label>
                    ))}
                  </fieldset>
                  <div className="br-rating-actions">
                    <Button
                      disabled={saving || !selected}
                      onClick={() => void submit()}
                    >
                      {saving ? "Salvando…" : "Salvar avaliação"}
                    </Button>
                    {summary.myRating && (
                      <Button
                        variant="ghost"
                        disabled={saving}
                        onClick={() => void submit(true)}
                      >
                        Remover minha avaliação
                      </Button>
                    )}
                  </div>
                </>
              )}
              {notice && <p role="status">{notice}</p>}
            </>
          )}
          {onComments && (
            <Button variant="ghost" onClick={onComments}>
              <Icon name="comment" />
              Ver comentários sobre o livro
            </Button>
          )}
        </section>
      </div>
    </Drawer>
  );
}

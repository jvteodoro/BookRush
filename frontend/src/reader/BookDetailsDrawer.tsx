import { useEffect, useRef, useState } from "react";
import { Drawer } from "../components/ui/Drawer";
import { Button } from "../components/ui/Button";
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
};
export function BookDetailsDrawer({
  book,
  onClose,
}: {
  book: DetailsBook;
  onClose: () => void;
}) {
  const [summary, setSummary] = useState<RatingSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState(0);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const lock = useRef(false);
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
    <Drawer
      title="Sobre o livro e avaliações"
      subtitle={book.title}
      onClose={onClose}
    >
      {book.author && <p>{book.author}</p>}
      {book.description && (
        <p className="br-book-description">{book.description}</p>
      )}
      <section aria-label="Avaliações do livro" aria-busy={loading || saving}>
        <h3>Avaliações dos leitores</h3>
        {loading ? (
          <p role="status">Carregando avaliações…</p>
        ) : (
          <>
            {summary && <p role="status">{ratingLabel(summary)}</p>}
            {error && <p role="alert">{error}</p>}
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
                      <span aria-hidden="true" data-filled={stars <= selected}>
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
      </section>
    </Drawer>
  );
}

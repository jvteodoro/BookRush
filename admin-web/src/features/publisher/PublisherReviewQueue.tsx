import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Notice } from "@bookrush/ui/Notice";
import { Button } from "../../components/ui/Button";
import { publishingApi, type Submission } from "./publisherMetrics";

export function PublisherReviewQueue({ books }: { books: Submission[] }) {
  const client = useQueryClient();
  const queue = useQuery({
    queryKey: ["publisher-editorial-review"],
    queryFn: publishingApi.reviewQueue,
    retry: false,
  });
  const decision = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      publishingApi.review(id, status),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["publisher-editorial-review"],
      });
      void client.invalidateQueries({ queryKey: ["publisher-submissions"] });
    },
  });
  const withdraw = useMutation({
    mutationFn: publishingApi.withdraw,
    onSuccess: () =>
      void client.invalidateQueries({ queryKey: ["publisher-submissions"] }),
  });
  return (
    <>
      {Array.isArray(queue.data) && queue.data.length > 0 && (
        <section aria-label="Revisão editorial">
          <h2>Revisão editorial</h2>
          <p>
            Confira o arquivo na fila administrativa antes de aprovar sua
            distribuição pública.
          </p>
          {queue.data.map((book) => (
            <div key={book.submission_id}>
              <strong>{book.title}</strong>{" "}
              <Button
                disabled={decision.isPending}
                onClick={() =>
                  decision.mutate({
                    id: book.submission_id,
                    status: "APPROVED",
                  })
                }
              >
                Aprovar publicação
              </Button>{" "}
              <Button
                disabled={decision.isPending}
                onClick={() =>
                  decision.mutate({
                    id: book.submission_id,
                    status: "REJECTED",
                  })
                }
              >
                Rejeitar
              </Button>
            </div>
          ))}
        </section>
      )}
      {books
        .filter((book) => book.status === "PUBLISHED")
        .map((book) => (
          <div key={book.id}>
            <span>{book.title} · Publicado</span>{" "}
            <Button
              disabled={withdraw.isPending}
              onClick={() => withdraw.mutate(book.id)}
            >
              Retirar publicação
            </Button>
          </div>
        ))}
      {(decision.isError || withdraw.isError) && (
        <Notice role="alert">
          Não foi possível atualizar a publicação. Confira o arquivo, a
          autorização de distribuição e tente novamente.
        </Notice>
      )}
    </>
  );
}

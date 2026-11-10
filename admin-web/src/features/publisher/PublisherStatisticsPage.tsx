import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Notice } from "@bookrush/ui/Notice";
import { PageHeader } from "@bookrush/ui/PageHeader";
import { Button } from "../../components/ui/Button";
import {
  metricLabels,
  metricTotals,
  publisherApi,
  type Metric,
} from "./publisherMetrics";
import "./publisher-statistics.css";

const number = new Intl.NumberFormat("pt-BR");
const keys = Object.keys(metricLabels) as Metric[];
export function PublisherStatisticsPage() {
  const [selected, setSelected] = useState("");
  const [days, setDays] = useState(30);
  const submissions = useQuery({
    queryKey: ["publisher-submissions"],
    queryFn: ({ signal }) => publisherApi.submissions(signal),
  });
  const books = submissions.data ?? [];
  const id = books.some((book) => book.id === selected)
    ? selected
    : books[0]?.id;
  const metrics = useQuery({
    queryKey: ["publisher-metrics", id, days],
    queryFn: ({ signal }) => publisherApi.metrics(id!, days, signal),
    enabled: Boolean(id),
  });
  const title = books.find((book) => book.id === id)?.title;
  return (
    <section className="publisher-statistics">
      <PageHeader>
        <div>
          <p>PUBLICADOR</p>
          <h1>Estatísticas dos meus livros</h1>
          <p>Acompanhe o desempenho das suas publicações.</p>
        </div>
      </PageHeader>
      {submissions.isPending ? (
        <p role="status">Carregando suas publicações…</p>
      ) : submissions.isError ? (
        <Notice role="alert">
          Não foi possível carregar suas publicações.{" "}
          <Button onClick={() => void submissions.refetch()}>
            Tentar novamente
          </Button>
        </Notice>
      ) : !books.length ? (
        <Notice>Nenhuma publicação encontrada para sua conta.</Notice>
      ) : (
        <>
          <div className="publisher-statistics__filters">
            <label>
              Livro
              <select
                aria-label="Livro"
                value={id}
                onChange={(event) => setSelected(event.target.value)}
              >
                {books.map((book) => (
                  <option key={book.id} value={book.id}>
                    {book.title} · {book.status}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Período
              <select
                id="publisher-statistics-days"
                aria-label="Período"
                value={days}
                onChange={(event) => setDays(Number(event.target.value))}
              >
                {[7, 30, 90, 365].map((value) => (
                  <option key={value} value={value}>
                    Últimos {value} dias
                  </option>
                ))}
              </select>
            </label>
            <Button
              onClick={() => void metrics.refetch()}
              disabled={metrics.isFetching}
            >
              Atualizar estatísticas
            </Button>
          </div>
          {metrics.isPending ? (
            <p role="status">Carregando estatísticas…</p>
          ) : metrics.isError ? (
            <Notice role="alert">
              Não foi possível consultar as estatísticas. Verifique sua sessão e
              tente novamente.{" "}
              <Button onClick={() => void metrics.refetch()}>
                Tentar novamente
              </Button>
            </Notice>
          ) : metrics.data?.status === "NOT_LINKED" ? (
            <Notice>
              Esta publicação ainda aguarda vínculo publicado no catálogo. As
              estatísticas estarão disponíveis depois desse vínculo.
            </Notice>
          ) : (
            metrics.data && (
              <>
                <h2>{title}</h2>
                <p>
                  Totais dos últimos {days} dias, incluindo hoje. Agregados
                  diários atualizados periodicamente; não são dados em tempo
                  real.
                </p>
                <div className="metrics" aria-label="Totais do livro">
                  {Object.entries(metricTotals(metrics.data.items)).map(
                    ([key, value]) => (
                      <div key={key}>
                        <span>{metricLabels[key as Metric]}</span>
                        <strong>{number.format(value)}</strong>
                      </div>
                    ),
                  )}
                </div>
                <p className="publisher-statistics__legend">
                  Impressões: exibições registradas. Aberturas: acessos ao
                  leitor. Leituras: eventos de leitura contabilizados pela
                  plataforma; não representam necessariamente leitores únicos.
                </p>
                {!metrics.data.items.length ? (
                  <Notice>Nenhuma atividade registrada neste período.</Notice>
                ) : (
                  <div className="publisher-statistics__table">
                    <table>
                      <caption>Estatísticas diárias de {title}</caption>
                      <thead>
                        <tr>
                          <th scope="col">Dia</th>
                          {keys.map((key) => (
                            <th scope="col" key={key}>
                              {metricLabels[key]}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {metrics.data.items.map((row) => (
                          <tr key={row.day}>
                            <th scope="row">
                              {row.day
                                .slice(0, 10)
                                .split("-")
                                .reverse()
                                .join("/")}
                            </th>
                            {keys.map((key) => (
                              <td key={key}>{number.format(row[key])}</td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </>
            )
          )}
        </>
      )}
    </section>
  );
}

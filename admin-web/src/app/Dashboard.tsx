import { PageHeader } from "@bookrush/ui/PageHeader";
import { useQuery } from "@tanstack/react-query";
import { ArrowUpRight, BarChart3, BookOpen, FlaskConical } from "lucide-react";
import { Link } from "react-router-dom";
import { services } from "../lib/services";
import { Card } from "../components/ui/Card";

export function Dashboard() {
  const books = useQuery({
    queryKey: ["books"],
    queryFn: () => services.listBooks(),
  });
  const runs = useQuery({
    queryKey: ["analytics"],
    queryFn: () => services.listAnalytics(),
  });
  const imported =
    books.data?.filter((b) => b.status === "IMPORTED").length ?? 0;
  return (
    <section>
      <PageHeader className="hero">
        <div>
          <span className="eyebrow">BOOKRUSH · ADMINISTRAÇÃO</span>
          <h1>Histórias em boas mãos.</h1>
          <p>
            Uma console única para construir o corpus, executar modelos e gerar
            os primeiros sinais comportamentais do algoritmo.
          </p>
        </div>
      </PageHeader>
      <div className="stats">
        <Card>
          <BookOpen />
          <span>Livros importados</span>
          <strong>{imported}</strong>
        </Card>
        <Card>
          <BarChart3 />
          <span>Análises executadas</span>
          <strong>{runs.data?.length ?? 0}</strong>
        </Card>
        <Card>
          <FlaskConical />
          <span>Treinamento</span>
          <strong>Pronto</strong>
        </Card>
      </div>
      <div className="quick">
        <Link to="/catalog">
          <span>01</span>
          <div>
            <strong>Organizar catálogo</strong>
            <p>Escolha e importe livros públicos.</p>
          </div>
          <ArrowUpRight />
        </Link>
        <Link to="/analytics">
          <span>02</span>
          <div>
            <strong>Executar análises</strong>
            <p>Produza features e trechos candidatos.</p>
          </div>
          <ArrowUpRight />
        </Link>
        <Link to="/feed-lab">
          <span>03</span>
          <div>
            <strong>Treinar o feed</strong>
            <p>Role, reaja e ajuste pesos em tempo real.</p>
          </div>
          <ArrowUpRight />
        </Link>
      </div>
    </section>
  );
}

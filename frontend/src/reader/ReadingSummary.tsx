import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import type { ReaderTab } from "../components/layout/AppShell";

type Props = {
  streak?: number;
  savedCount: number;
  onNavigate: (tab: ReaderTab) => void;
};

export function ReadingSummary({ streak, savedCount, onNavigate }: Props) {
  return (
    <section className="br-reading-summary" aria-label="Sua leitura">
      <Card className="br-account-panel">
        <h2>
          {typeof streak === "number"
            ? `${streak} dias de sequência`
            : "Sua leitura"}
        </h2>
        <p>Continue seu hábito, uma história de cada vez.</p>
        <Button variant="ghost" onClick={() => onNavigate("plant")}>
          Ver minha planta
        </Button>
      </Card>
      <Card className="br-account-panel">
        <h2>Feito para você</h2>
        <p>
          Descubra histórias do seu catálogo e salve as que quiser ler depois.
        </p>
        <Button variant="ghost" onClick={() => onNavigate("recommendations")}>
          Explorar recomendações
        </Button>
      </Card>
      <Card className="br-account-panel">
        <h2>Sua biblioteca</h2>
        <p>
          {savedCount} {savedCount === 1 ? "livro salvo" : "livros salvos"} para
          sua próxima leitura.
        </p>
        <Button variant="ghost" onClick={() => onNavigate("library")}>
          Ver livros salvos
        </Button>
      </Card>
    </section>
  );
}

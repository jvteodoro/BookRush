import { FollowerCount } from "./FollowerCount";
import type { ReactNode } from "react";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import type { ReaderTab } from "../components/layout/AppShell";
import { PrivacyPage } from "./PrivacyPage";
import plant0 from "../assets/planta/estagio-0.png";
import plant1 from "../assets/planta/estagio-1.png";
import plant2 from "../assets/planta/estagio-2.png";
import plant3 from "../assets/planta/estagio-3.png";
import plant4 from "../assets/planta/estagio-4.png";
import plant5 from "../assets/planta/estagio-5.png";
import sad1 from "../assets/planta/estagio-1-triste.png";
import sad2 from "../assets/planta/estagio-2-triste.png";
import sad3 from "../assets/planta/estagio-3-triste.png";
import sad4 from "../assets/planta/estagio-4-triste.png";
import sad5 from "../assets/planta/estagio-5-triste.png";

const plants = [plant0, plant1, plant2, plant3, plant4, plant5];
const sadPlants = [plant0, sad1, sad2, sad3, sad4, sad5];

type AccountRuntime = {
  __BOOKRUSH_PROFILE__?: {
    displayName?: string;
    bio?: string;
    isPublic?: boolean;
  } | null;
  __BOOKRUSH_ADMIN_USERS__?: unknown[];
  __BOOKRUSH_ADMIN_REPORTS__?: unknown[];
  __BOOKRUSH_PUBLISHER_SUBMISSIONS__?: unknown[];
  __BOOKRUSH_PLANT_SAVE_ERROR__?: boolean;
};
type PlantState = {
  formulaVersion?: string;
  stage?: number;
  streakDays?: number;
  totalReadingMinutes?: number;
  measuredUntil?: string;
  sad?: boolean;
  todaySeconds?: number;
  goalSeconds?: number;
  goalReached?: boolean;
};

const accountRuntime = () => window as unknown as AccountRuntime;

type Props = {
  tab: Extract<
    ReaderTab,
    "plant" | "profile" | "privacy" | "admin" | "publisher" | "prototype"
  >;
  userName: string;
  savedCount: number;
  streak?: number;
  plant?: PlantState | null;
  plantLoading?: boolean;
  plantError?: boolean;
  onNavigate: (tab: ReaderTab) => void;
};

function PageHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <div className="br-account-heading">
      <span className="br-kicker">{eyebrow}</span>
      <h1>{title}</h1>
      <p className="br-muted">{description}</p>
    </div>
  );
}

function ActionRow({ children }: { children: ReactNode }) {
  return <div className="br-account-actions">{children}</div>;
}

export function AccountPage({
  tab,
  userName,
  savedCount,
  streak = 0,
  plant,
  plantLoading,
  plantError,
  onNavigate,
}: Props) {
  const profile = accountRuntime().__BOOKRUSH_PROFILE__;
  const displayName = profile?.displayName?.trim() || userName;
  if (tab === "plant") {
    const stage = Math.max(0, Math.min(5, Math.trunc(plant?.stage ?? 0)));
    return (
      <section className="br-account-page">
        <PageHeading
          eyebrow="HÁBITO DE LEITURA"
          title="Minha planta"
          description="Acompanhe o crescimento do seu hábito de leitura."
        />
        <Card className="br-account-hero br-plant-card">
          {plantLoading && <p role="status">Carregando sua planta…</p>}
          {plantError && (
            <p role="alert">
              Não foi possível atualizar sua planta. Tente abrir esta página
              novamente.
            </p>
          )}
          {accountRuntime().__BOOKRUSH_PLANT_SAVE_ERROR__ && (
            <p role="alert">
              Parte do tempo de leitura não pôde ser salva. Verifique sua
              conexão.
            </p>
          )}
          {!plantLoading && !plantError && plant && (
            <>
              <div className="br-plant-art">
                <img
                  src={(plant?.sad ? sadPlants : plants)[stage]}
                  alt={`Planta de leitura no estágio ${stage}${plant?.sad && stage > 0 ? ", triste" : ""}`}
                />
              </div>
              <div>
                <span className="br-kicker">SEQUÊNCIA ATUAL</span>
                <strong className="br-account-number">{streak}</strong>
                <span className="br-account-unit">dias</span>
                <p>
                  {plant.sad
                    ? "Sua plantinha precisa de uma leitura para se recuperar."
                    : "Leia 15 minutos por dia para cultivar sua plantinha."}
                </p>
                {typeof plant.todaySeconds === "number" && (
                  <p>
                    Hoje: {Math.min(15, Math.floor(plant.todaySeconds / 60))} de
                    15 minutos{plant.goalReached ? " — meta cumprida!" : ""}
                  </p>
                )}
                {stage === 5 && (
                  <p>Parabéns! Sua plantinha atingiu o nível máximo!</p>
                )}
              </div>
              <div className="br-account-stats">
                <span>
                  <b>{savedCount}</b> livros salvos
                </span>
                <span>
                  <b>{plant?.totalReadingMinutes ?? 0}</b> min lidos
                </span>
                <span>
                  <b>{plant?.stage ?? 0}</b>/5 estágio da planta
                </span>
              </div>
              <ActionRow>
                <Button onClick={() => onNavigate("feed")}>
                  Encontrar uma leitura
                </Button>
                <Button variant="ghost" onClick={() => onNavigate("library")}>
                  Ver biblioteca
                </Button>
              </ActionRow>
            </>
          )}
        </Card>
      </section>
    );
  }

  if (tab === "profile") {
    return (
      <section className="br-account-page">
        <PageHeading
          eyebrow="SEU ESPAÇO"
          title="Perfil"
          description="Seu ritmo de leitura, suas escolhas e suas histórias."
        />
        <Card className="br-account-hero br-profile-hero">
          <span className="br-account-avatar" aria-hidden="true">
            {displayName.slice(0, 1).toUpperCase()}
          </span>
          <div>
            <h2>{displayName}</h2>
            <FollowerCount />
            <p>
              {profile?.bio?.trim() ||
                "Entre um capítulo e outro, descobrindo novos mundos."}
            </p>
            <span className="br-muted">
              {savedCount} livros na biblioteca · {streak} dias de leitura
            </span>
          </div>
        </Card>
        <Card className="br-account-panel">
          <h2>Preferências da conta</h2>
          <p className="br-muted">
            Ajuste o que deseja compartilhar e mantenha o BookRush com a sua
            cara.
          </p>
          <ActionRow>
            <Button onClick={() => onNavigate("privacy")}>Privacidade</Button>
            <Button variant="ghost" onClick={() => onNavigate("library")}>
              Abrir biblioteca
            </Button>
          </ActionRow>
        </Card>
      </section>
    );
  }

  if (tab === "privacy") {
    return <PrivacyPage onBack={() => onNavigate("profile")} />;
  }

  if (tab === "admin") {
    const users = accountRuntime().__BOOKRUSH_ADMIN_USERS__ ?? [];
    const reports = accountRuntime().__BOOKRUSH_ADMIN_REPORTS__ ?? [];
    return (
      <section className="br-account-page">
        <PageHeading
          eyebrow="OPERAÇÃO BOOKRUSH"
          title="Administração"
          description="Acompanhe a plataforma sem sair do mesmo design system."
        />
        <div className="br-account-grid">
          <Card className="br-account-stat">
            <span className="br-kicker">USUÁRIOS</span>
            <strong>{users.length}</strong>
            <p>Identidades consultáveis</p>
          </Card>
          <Card className="br-account-stat">
            <span className="br-kicker">MODERAÇÃO</span>
            <strong>{reports.length}</strong>
            <p>Denúncias na fila</p>
          </Card>
        </div>
        <Card className="br-account-panel">
          <h2>Ferramentas administrativas</h2>
          <p className="br-muted">
            As operações detalhadas continuam disponíveis na console
            administrativa protegida.
          </p>
          <ActionRow>
            <Button onClick={() => window.location.assign("/admin/")}>
              Abrir console admin
            </Button>
            <Button variant="ghost" onClick={() => onNavigate("feed")}>
              Voltar para o feed
            </Button>
          </ActionRow>
        </Card>
      </section>
    );
  }

  if (tab === "publisher") {
    return (
      <section className="br-account-page">
        <PageHeading
          eyebrow="PORTAL DO PUBLICADOR"
          title="Publicador"
          description="Acompanhe suas publicações e envie novas histórias."
        />
        <Card className="br-account-panel">
          <h2>Suas publicações</h2>
          <strong className="br-account-number">—</strong>
          <p className="br-muted">
            O portal de publicações usa a console protegida do realm de
            plataforma. Abra a console administrativa para continuar.
          </p>
          <ActionRow>
            <Button onClick={() => window.location.assign("/admin/")}>
              Abrir console
            </Button>
            <Button variant="ghost" onClick={() => onNavigate("feed")}>
              Voltar para o feed
            </Button>
          </ActionRow>
        </Card>
      </section>
    );
  }

  return (
    <section className="br-account-page">
      <PageHeading
        eyebrow="BOOKRUSH"
        title="Menu do protótipo"
        description="Explore as áreas construídas com o design system atual."
      />
      <Card className="br-account-panel">
        <ActionRow>
          <Button onClick={() => onNavigate("feed")}>Para você</Button>
          <Button variant="ghost" onClick={() => onNavigate("admin")}>
            Administração
          </Button>
          <Button variant="ghost" onClick={() => onNavigate("publisher")}>
            Publicador
          </Button>
        </ActionRow>
      </Card>
    </section>
  );
}

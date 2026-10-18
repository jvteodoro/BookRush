import type { ReactNode } from "react";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import type { ReaderTab } from "../components/layout/AppShell";
import { PrivacyPage } from "./PrivacyPage";

type AccountRuntime = {
  __BOOKRUSH_PROFILE__?: {
    displayName?: string;
    bio?: string;
    isPublic?: boolean;
  } | null;
  __BOOKRUSH_ADMIN_USERS__?: unknown[];
  __BOOKRUSH_ADMIN_REPORTS__?: unknown[];
  __BOOKRUSH_PUBLISHER_SUBMISSIONS__?: unknown[];
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
  onNavigate,
}: Props) {
  const profile = accountRuntime().__BOOKRUSH_PROFILE__;
  const displayName = profile?.displayName?.trim() || userName;

  if (tab === "plant") {
    return (
      <section className="br-account-page">
        <PageHeading
          eyebrow="HÁBITO DE LEITURA"
          title="Minha planta"
          description="Acompanhe o crescimento do seu hábito de leitura."
        />
        <Card className="br-account-hero br-plant-card">
          <div className="br-plant-art" aria-hidden="true">
            🌱
          </div>
          <div>
            <span className="br-kicker">SEQUÊNCIA ATUAL</span>
            <strong className="br-account-number">{streak}</strong>
            <span className="br-account-unit">dias</span>
            <p>Cada capítulo cultivado conta para a próxima folha.</p>
          </div>
          <div className="br-account-stats">
            <span>
              <b>{savedCount}</b> livros salvos
            </span>
            <span>
              <b>4</b> min lidos hoje
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
    const submissions =
      accountRuntime().__BOOKRUSH_PUBLISHER_SUBMISSIONS__ ?? [];
    return (
      <section className="br-account-page">
        <PageHeading
          eyebrow="PORTAL DO PUBLICADOR"
          title="Publicador"
          description="Acompanhe suas publicações e envie novas histórias."
        />
        <Card className="br-account-panel">
          <h2>Suas publicações</h2>
          <strong className="br-account-number">{submissions.length}</strong>
          <p className="br-muted">
            rascunhos e publicações retornados pelo serviço.
          </p>
          <ActionRow>
            <Button onClick={() => onNavigate("feed")}>
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

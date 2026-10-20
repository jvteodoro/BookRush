import { useEffect, useState } from "react";
import { authClient } from "../auth";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import {
  DEFAULT_PRIVACY,
  loadPrivacy,
  normalizePrivacy,
  savePrivacy,
  type PrivacyPreferences,
  type ReaderProfile,
  type Visibility,
} from "./privacy";

type AccountRuntime = Window & {
  __BOOKRUSH_PROFILE__?: ReaderProfile | null;
};

const OPTIONS: Array<{ value: Visibility; label: string }> = [
  { value: "PUBLIC", label: "Todos" },
  { value: "FOLLOWERS", label: "Seguidores" },
  { value: "PRIVATE", label: "Somente eu" },
];

const authenticatedFetch = authClient.fetch.bind(authClient);

const SETTINGS: Array<{
  key: keyof PrivacyPreferences;
  label: string;
  description: string;
}> = [
  {
    key: "profileVisibility",
    label: "Perfil",
    description: "Quem pode encontrar seu perfil e ler sua biografia.",
  },
  {
    key: "activityVisibility",
    label: "Atividade",
    description: "Quem pode ver curtidas, comentários e atividades de leitura.",
  },
  {
    key: "libraryVisibility",
    label: "Biblioteca",
    description: "Quem pode consultar os livros salvos na sua estante.",
  },
  {
    key: "recentVisibility",
    label: "Leituras recentes",
    description: "Quem pode ver os livros que você abriu recentemente.",
  },
  {
    key: "socialGraphVisibility",
    label: "Seguidores e seguindo",
    description: "Quem pode consultar suas conexões com outros leitores.",
  },
];

export function PrivacyPage({ onBack }: { onBack: () => void }) {
  const runtime = window as AccountRuntime;
  const [preferences, setPreferences] = useState<PrivacyPreferences>(() =>
    normalizePrivacy(runtime.__BOOKRUSH_PROFILE__),
  );
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    void loadPrivacy(authenticatedFetch)
      .then(({ preferences: loaded }) => {
        if (!active) return;
        setPreferences(loaded);
      })
      .catch((cause) => {
        if (active)
          setError(
            cause instanceof Error
              ? cause.message
              : "Não foi possível carregar as preferências de privacidade.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const update = (key: keyof PrivacyPreferences, value: Visibility) => {
    setPreferences((current) => ({ ...current, [key]: value }));
    setMessage("");
  };

  const submit = async () => {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const saved = await savePrivacy(preferences, authenticatedFetch);
      setPreferences(saved.preferences);
      setMessage("Preferências de privacidade salvas.");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Não foi possível salvar as preferências de privacidade.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="br-account-page">
      <div className="br-account-heading">
        <span className="br-kicker">CONTROLE DE VISIBILIDADE</span>
        <h1>Privacidade</h1>
        <p className="br-muted">
          Escolha uma audiência diferente para cada parte da sua experiência.
        </p>
      </div>
      <Card className="br-account-panel br-privacy-panel">
        <h2>Preferências de compartilhamento</h2>
        <p className="br-muted">
          “Todos” permite acesso público; “Seguidores” limita a quem acompanha
          você; “Somente eu” mantém o conteúdo privado.
        </p>
        {loading ? (
          <p className="br-account-status" role="status">
            Carregando preferências…
          </p>
        ) : (
          <div className="br-privacy-grid">
            {SETTINGS.map((setting) => (
              <label className="br-privacy-field" key={setting.key}>
                <span>
                  <strong>{setting.label}</strong>
                  <small>{setting.description}</small>
                </span>
                <select
                  aria-label={`Visibilidade de ${setting.label}`}
                  disabled={saving}
                  value={
                    preferences[setting.key] ?? DEFAULT_PRIVACY[setting.key]
                  }
                  onChange={(event) =>
                    update(setting.key, event.target.value as Visibility)
                  }
                >
                  {OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
        )}
        {error && (
          <p
            className="br-account-status br-account-status--error"
            role="alert"
          >
            {error}
          </p>
        )}
        {message && (
          <p className="br-account-status" role="status">
            {message}
          </p>
        )}
        <div className="br-account-actions">
          <Button disabled={loading || saving} onClick={() => void submit()}>
            {saving ? "Salvando…" : "Salvar alterações"}
          </Button>
          <Button variant="ghost" disabled={saving} onClick={onBack}>
            Voltar ao perfil
          </Button>
        </div>
      </Card>
    </section>
  );
}

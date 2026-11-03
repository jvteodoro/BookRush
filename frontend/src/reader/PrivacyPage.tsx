import { useEffect, useState } from "react";
import { authClient } from "../auth";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { Icon, type IconName } from "../components/ui/Icon";
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
  icon: IconName;
}> = [
  {
    key: "profileVisibility",
    icon: "user",
    label: "Perfil",
    description: "Quem pode encontrar seu perfil e ler sua biografia.",
  },
  {
    key: "activityVisibility",
    icon: "heart",
    label: "Atividade",
    description: "Quem pode ver curtidas, comentários e atividades de leitura.",
  },
  {
    key: "libraryVisibility",
    icon: "library",
    label: "Biblioteca",
    description: "Quem pode consultar os livros salvos na sua estante.",
  },
  {
    key: "recentVisibility",
    icon: "bookmark",
    label: "Leituras recentes",
    description: "Quem pode ver os livros que você abriu recentemente.",
  },
  {
    key: "socialGraphVisibility",
    icon: "share",
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
        <div className="br-privacy-intro">
          <span className="br-privacy-symbol" aria-hidden="true">
            <Icon name="shield" />
          </span>
          <div>
            <h2>Você escolhe o que compartilhar</h2>
            <p className="br-muted">
              Compartilhe com todos, apenas com seus seguidores ou guarde só
              para você.
            </p>
          </div>
        </div>
        {loading ? (
          <p className="br-account-status" role="status">
            Carregando preferências…
          </p>
        ) : (
          <div className="br-privacy-grid">
            {SETTINGS.map((setting) => (
              <fieldset
                className="br-privacy-field"
                key={setting.key}
                disabled={saving}
                aria-describedby={`${setting.key}-description`}
              >
                <legend className="br-sr-only">
                  Visibilidade de {setting.label}
                </legend>
                <div className="br-privacy-description">
                  <span className="br-privacy-setting-icon" aria-hidden="true">
                    <Icon name={setting.icon} />
                  </span>
                  <div>
                    <strong>{setting.label}</strong>
                    <p id={`${setting.key}-description`}>
                      {setting.description}
                    </p>
                  </div>
                </div>
                <div className="br-privacy-choices">
                  {OPTIONS.map((option) => (
                    <label className="br-privacy-choice" key={option.value}>
                      <input
                        className="br-sr-only"
                        type="radio"
                        name={setting.key}
                        value={option.value}
                        checked={
                          (preferences[setting.key] ??
                            DEFAULT_PRIVACY[setting.key]) === option.value
                        }
                        onChange={() => update(setting.key, option.value)}
                      />
                      <span>{option.label}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
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

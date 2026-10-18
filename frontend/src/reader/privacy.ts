export type Visibility = "PUBLIC" | "FOLLOWERS" | "PRIVATE";

export type PrivacyPreferences = {
  profileVisibility: Visibility;
  activityVisibility: Visibility;
  libraryVisibility: Visibility;
  recentVisibility: Visibility;
  socialGraphVisibility: Visibility;
};

export type ReaderProfile = Partial<PrivacyPreferences> & {
  displayName?: string;
  bio?: string;
  isPublic?: boolean;
};

export type PrivacyTransport = (
  input: string,
  init?: RequestInit,
) => Promise<Response>;

export const DEFAULT_PRIVACY: PrivacyPreferences = {
  profileVisibility: "PUBLIC",
  activityVisibility: "PUBLIC",
  libraryVisibility: "PRIVATE",
  recentVisibility: "PRIVATE",
  socialGraphVisibility: "PUBLIC",
};

const VISIBILITIES = new Set<Visibility>(["PUBLIC", "FOLLOWERS", "PRIVATE"]);

function visibility(value: unknown, fallback: Visibility): Visibility {
  return typeof value === "string" && VISIBILITIES.has(value as Visibility)
    ? (value as Visibility)
    : fallback;
}

export function normalizePrivacy(
  profile?: ReaderProfile | null,
): PrivacyPreferences {
  const profileFallback = profile?.isPublic === false ? "PRIVATE" : "PUBLIC";
  return {
    profileVisibility: visibility(profile?.profileVisibility, profileFallback),
    activityVisibility: visibility(
      profile?.activityVisibility,
      DEFAULT_PRIVACY.activityVisibility,
    ),
    libraryVisibility: visibility(
      profile?.libraryVisibility,
      DEFAULT_PRIVACY.libraryVisibility,
    ),
    recentVisibility: visibility(
      profile?.recentVisibility,
      DEFAULT_PRIVACY.recentVisibility,
    ),
    socialGraphVisibility: visibility(
      profile?.socialGraphVisibility,
      DEFAULT_PRIVACY.socialGraphVisibility,
    ),
  };
}

function requestError(status: number, action: "carregar" | "salvar"): Error {
  if (status === 401)
    return new Error("Sua sessão expirou. Entre novamente para continuar.");
  if (status === 403)
    return new Error("Sua conta não pode alterar estas preferências.");
  return new Error(
    `Não foi possível ${action} as preferências de privacidade.`,
  );
}

export async function loadPrivacy(
  transport: PrivacyTransport,
): Promise<{ profile: ReaderProfile; preferences: PrivacyPreferences }> {
  const response = await transport("/api/v1/profile");
  if (!response.ok) throw requestError(response.status, "carregar");
  const profile = (await response.json()) as ReaderProfile;
  return { profile, preferences: normalizePrivacy(profile) };
}

export async function savePrivacy(
  preferences: PrivacyPreferences,
  transport: PrivacyTransport,
): Promise<{ profile: ReaderProfile; preferences: PrivacyPreferences }> {
  const response = await transport("/api/v1/profile", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(preferences),
  });
  if (!response.ok) throw requestError(response.status, "salvar");
  const profile = (await response.json()) as ReaderProfile;
  return { profile, preferences: normalizePrivacy(profile) };
}

import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  AppState,
  ScrollView,
  FlatList,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";
import { StatusBar } from "expo-status-bar";
import {
  Book,
  ApiError,
  authenticatedRequest,
  listBooks,
  listLibrary,
  sendBehaviorEvents,
  updateLibrary,
} from "./src/api";
import {
  accessMessage,
  allowsProduct,
  createSessionGate,
  invitationAction,
  invitationRequest,
  inviteAddressPath,
  invitationsPath,
  type InviteAddress,
  type Invitation,
  type Request,
  type Session,
  type SessionView,
} from "../packages/session/session";
import { updateSavedBook } from "./src/updateSavedBook";
import { createTelemetry } from "./src/telemetry";

WebBrowser.maybeCompleteAuthSession();
const authority =
  process.env.EXPO_PUBLIC_OIDC_AUTHORITY ??
  "https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush";
const clientId = process.env.EXPO_PUBLIC_OIDC_CLIENT_ID ?? "bookrush-mobile";
const redirectUri = AuthSession.makeRedirectUri({
  scheme: "bookrush",
  path: "callback",
});
const discovery: AuthSession.DiscoveryDocument = {
  authorizationEndpoint: `${authority}/protocol/openid-connect/auth`,
  tokenEndpoint: `${authority}/protocol/openid-connect/token`,
  revocationEndpoint: `${authority}/protocol/openid-connect/revoke`,
  endSessionEndpoint: `${authority}/protocol/openid-connect/logout`,
};

const authConfig: AuthSession.AuthRequestConfig = {
  clientId,
  redirectUri,
  responseType: AuthSession.ResponseType.Code,
  scopes: ["openid", "profile", "email", "session.view", "family.invites"],
  usePKCE: true,
  prompt: AuthSession.Prompt.Login,
};
const registrationDiscovery: AuthSession.DiscoveryDocument = {
  ...discovery,
  authorizationEndpoint: `${authority}/protocol/openid-connect/registrations`,
};

export default function App() {
  const [request, response, promptAsync] = AuthSession.useAuthRequest(
    authConfig,
    discovery,
  );
  const [registration, registrationResponse, registerAsync] =
    AuthSession.useAuthRequest(authConfig, registrationDiscovery);
  const [credential, setCredential] = useState<{
    epoch: number;
    token: AuthSession.TokenResponse;
  }>();
  const epoch = useRef(0);
  const exchangeInFlight = useRef<string>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (
      response?.type === "success" &&
      response.params.code &&
      request?.codeVerifier
    )
      void exchangeCode(response.params.code, request.codeVerifier);
  }, [response]);
  useEffect(() => {
    if (
      registrationResponse?.type === "success" &&
      registrationResponse.params.code &&
      registration?.codeVerifier
    )
      void exchangeCode(
        registrationResponse.params.code,
        registration.codeVerifier,
      );
  }, [registrationResponse]);
  async function exchangeCode(code: string, verifier: string) {
    if (exchangeInFlight.current === code) return;
    exchangeInFlight.current = code;
    const current = ++epoch.current;
    setCredential(undefined);
    setBusy(true);
    setError("");
    try {
      const result = await AuthSession.exchangeCodeAsync(
        {
          clientId,
          code,
          redirectUri,
          extraParams: { code_verifier: verifier },
        },
        discovery,
      );
      if (epoch.current === current)
        setCredential({ epoch: current, token: result });
    } catch (cause) {
      if (epoch.current === current)
        setError(
          cause instanceof Error
            ? cause.message
            : "Não foi possível concluir o login.",
        );
    } finally {
      if (epoch.current === current) setBusy(false);
    }
  }
  function logout() {
    ++epoch.current;
    setCredential(undefined);
    setBusy(false);
    setError("");
  }
  if (!credential)
    return (
      <SafeAreaView style={styles.center}>
        <StatusBar style="light" />
        <Text style={styles.logo}>B</Text>
        <Text style={styles.kicker}>UM UNIVERSO DE HISTÓRIAS</Text>
        <Text style={styles.title}>Seu próximo{`\n`}capítulo começa aqui.</Text>
        <Text style={styles.subtitle}>
          Descubra livros que combinam com você.
        </Text>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <Pressable
          disabled={!request || busy}
          style={styles.primary}
          onPress={() => void promptAsync()}
        >
          <Text style={styles.primaryText}>
            {busy ? "Conectando…" : "Entrar com Keycloak  →"}
          </Text>
        </Pressable>
        <Pressable
          disabled={!registration || busy}
          onPress={() => void registerAsync()}
        >
          <Text style={styles.register}>
            Ainda não tem conta?{" "}
            <Text style={styles.registerStrong}>Cadastre-se</Text>
          </Text>
        </Pressable>
        <Text style={styles.hint}>
          Login seguro · Tokens ficam apenas na memória
        </Text>
      </SafeAreaView>
    );
  return (
    <MobileSession
      key={credential.epoch}
      initial={credential.token}
      onLogout={logout}
    />
  );
}

function MobileSession({
  initial,
  onLogout,
}: {
  initial: AuthSession.TokenResponse;
  onLogout: () => void;
}) {
  const identity = useRef(initial);
  const refreshInFlight = useRef<Promise<AuthSession.TokenResponse>>();
  const [view, setView] = useState<SessionView>({ status: "loading" });
  const [gate, setGate] = useState<ReturnType<typeof createSessionGate>>();
  const lastDenial = useRef(0);
  const alive = useRef(true);

  const freshToken = async (force = false) => {
    const current = identity.current;
    if (
      !current.refreshToken ||
      (!force &&
        current.issuedAt + (current.expiresIn ?? 3600) > Date.now() / 1000 + 30)
    )
      return current.accessToken;
    if (!refreshInFlight.current) {
      refreshInFlight.current = AuthSession.refreshAsync(
        { clientId, refreshToken: current.refreshToken },
        discovery,
      )
        .then((value) => {
          if (alive.current) {
            if (!value.refreshToken) value.refreshToken = current.refreshToken;
            identity.current = value;
          }
          return value;
        })
        .finally(() => {
          refreshInFlight.current = undefined;
        });
    }
    return (await refreshInFlight.current).accessToken;
  };
  const request: Request = async (path, init) => {
    let token = await freshToken();
    if (!alive.current || init?.signal?.aborted)
      throw new Error("Session changed");
    let response = await authenticatedRequest(token, path, init);
    if (
      response.status === 401 &&
      (init?.method ?? "GET") === "GET" &&
      identity.current.refreshToken
    ) {
      try {
        token = await freshToken(true);
      } catch {
        return response;
      }
      if (!alive.current || init?.signal?.aborted)
        throw new Error("Session changed");
      response = await authenticatedRequest(token, path, init);
    }
    return response;
  };
  useEffect(() => {
    alive.current = true;
    const session = createSessionGate(
      request,
      setView,
      () => AppState.currentState !== "background",
    );
    setGate(session);
    session.refresh();
    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") session.refresh();
    });
    return () => {
      alive.current = false;
      subscription.remove();
      session.dispose();
    };
  }, []);

  function observeFailure(cause: unknown) {
    if (
      cause instanceof ApiError &&
      [401, 403, 503].includes(cause.status) &&
      Date.now() - lastDenial.current > 3000
    ) {
      lastDenial.current = Date.now();
      gate?.refresh(true);
    }
  }
  async function productToken() {
    if (!alive.current || !gate?.allowsProduct())
      throw new Error("Acesso indisponível.");
    const token = await freshToken();
    if (!alive.current || !gate.allowsProduct())
      throw new Error("Acesso indisponível.");
    return token;
  }
  if (view.status === "ready" && allowsProduct(view)) {
    return (
      <MobileProduct
        getToken={productToken}
        onFailure={observeFailure}
        onLogout={onLogout}
      />
    );
  }
  if (view.status === "ready")
    return (
      <MobileRestricted
        session={view.session}
        request={request}
        onRefresh={() => gate?.refresh(true)}
        onLogout={onLogout}
      />
    );
  return (
    <SafeAreaView style={styles.center}>
      <Text style={styles.subtitle}>
        {view.status === "loading"
          ? "Verificando seu acesso…"
          : view.authenticate
            ? "Sua sessão precisa ser renovada."
            : "Não foi possível verificar seu acesso."}
      </Text>
      {view.status === "error" && (
        <Pressable
          style={styles.primary}
          onPress={view.authenticate ? onLogout : () => gate?.refresh(true)}
        >
          <Text style={styles.primaryText}>
            {view.authenticate ? "Entrar novamente" : "Tentar novamente"}
          </Text>
        </Pressable>
      )}
      <Pressable onPress={onLogout}>
        <Text style={styles.register}>Sair da conta</Text>
      </Pressable>
    </SafeAreaView>
  );
}

function MobileRestricted({
  session,
  request,
  onRefresh,
  onLogout,
}: {
  session: Session;
  request: Request;
  onRefresh: () => void;
  onLogout: () => void;
}) {
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [address, setAddress] = useState<InviteAddress>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const operations = useRef<AbortController>();
  const permitted = session.capabilities.includes("INVITE_SELF");
  useEffect(() => {
    const controller = new AbortController();
    operations.current = controller;
    setBusy(false);
    if (permitted)
      void invitationRequest<Invitation[]>(request, invitationsPath, {
        signal: controller.signal,
      })
        .then((result) => {
          if (!controller.signal.aborted) {
            setInvitations(result);
            setLoaded(true);
          }
        })
        .catch((cause) => {
          if (!controller.signal.aborted) setError(cause.message);
        });
    return () => controller.abort();
  }, [permitted, session.validUntil]);

  async function act(path: string, generate = false) {
    const controller = operations.current;
    if (
      !controller ||
      controller.signal.aborted ||
      Date.parse(session.validUntil) <= Date.now()
    )
      return;
    setBusy(true);
    setError("");
    try {
      const result = await invitationRequest<InviteAddress>(request, path, {
        method: "POST",
        signal: controller.signal,
      });
      if (controller.signal.aborted) return;
      if (generate) setAddress(result);
      else onRefresh();
    } catch (cause) {
      if (!controller.signal.aborted)
        setError(cause instanceof Error ? cause.message : "Falha no convite.");
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return (
    <SafeAreaView style={styles.root}>
      <ScrollView contentContainerStyle={styles.hero}>
        <Text style={styles.brand}>Olá, {session.username}</Text>
        <Text style={styles.subtitle}>{accessMessage(session)}</Text>
        <Pressable style={styles.primary} onPress={onRefresh}>
          <Text style={styles.primaryText}>Atualizar acesso</Text>
        </Pressable>
        {permitted && (
          <>
            <Text style={styles.heading}>Convites familiares</Text>
            {loaded && invitations.length === 0 && (
              <Text style={styles.subtitle}>Nenhum convite recebido.</Text>
            )}
            {invitations.map((invite) => (
              <View key={invite.id}>
                <Text style={styles.subtitle}>
                  Válido até{" "}
                  {new Date(invite.expiresAt).toLocaleString("pt-BR")}
                </Text>
                <Pressable
                  disabled={busy || invite.state !== "PENDING"}
                  onPress={() =>
                    void act(invitationAction(invite.id, "accept"))
                  }
                >
                  <Text style={styles.primaryText}>Aceitar</Text>
                </Pressable>
                <Pressable
                  disabled={busy || invite.state !== "PENDING"}
                  onPress={() =>
                    void act(invitationAction(invite.id, "decline"))
                  }
                >
                  <Text style={styles.primaryText}>Recusar</Text>
                </Pressable>
              </View>
            ))}
            <Pressable
              disabled={busy}
              style={styles.primary}
              onPress={() => void act(inviteAddressPath, true)}
            >
              <Text style={styles.primaryText}>Gerar código de convite</Text>
            </Pressable>
            {address && (
              <Text selectable style={styles.subtitle}>
                {address.code} · expira em{" "}
                {new Date(address.expiresAt).toLocaleString("pt-BR")}
              </Text>
            )}
          </>
        )}
        {error && (
          <Text accessibilityRole="alert" style={styles.error}>
            {error}
          </Text>
        )}
        <Pressable onPress={onLogout}>
          <Text style={styles.register}>Sair da conta</Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

function MobileProduct({
  getToken,
  onFailure,
  onLogout,
}: {
  getToken: () => Promise<string>;
  onFailure: (cause: unknown) => void;
  onLogout: () => void;
}) {
  const [books, setBooks] = useState<Book[]>([]);
  const [saved, setSaved] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<Book>();
  const pendingSaves = useRef(new Set<string>());
  const product = useRef<AbortController>();
  const [libraryReady, setLibraryReady] = useState(false);
  const tokenForRequest = useRef(getToken);
  const failureListener = useRef(onFailure);
  tokenForRequest.current = getToken;
  failureListener.current = onFailure;
  const telemetry = useRef<ReturnType<typeof createTelemetry>>();
  const accessToken = async (controller: AbortController) => {
    const token = await tokenForRequest.current();
    if (controller.signal.aborted) throw new Error("Session changed");
    return token;
  };
  function report(cause: unknown, controller: AbortController) {
    if (controller.signal.aborted) return;
    failureListener.current(cause);
    setError(
      cause instanceof Error
        ? cause.message
        : "Não foi possível concluir a operação.",
    );
  }
  useEffect(() => {
    const controller = new AbortController();
    product.current = controller;
    const events = createTelemetry(async (batch) => {
      try {
        await sendBehaviorEvents(
          await accessToken(controller),
          batch,
          controller.signal,
        );
      } catch (cause) {
        if (!controller.signal.aborted) failureListener.current(cause);
        throw cause;
      }
    });
    telemetry.current = events;
    void accessToken(controller)
      .then((token) => listLibrary(token, controller.signal))
      .then((value) => {
        if (!controller.signal.aborted) {
          setSaved(value);
          setLibraryReady(true);
        }
      })
      .catch((cause) => report(cause, controller));
    return () => {
      controller.abort();
      events.dispose();
    };
  }, []);
  async function loadBooks() {
    const controller = product.current;
    if (!controller || controller.signal.aborted) return;
    setBusy(true);
    setError("");
    try {
      const page = await listBooks(
        await accessToken(controller),
        query,
        controller.signal,
      );
      if (!controller.signal.aborted) setBooks(page.items);
    } catch (cause) {
      report(cause, controller);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  async function save(bookId: string) {
    const controller = product.current;
    if (
      !controller ||
      controller.signal.aborted ||
      !libraryReady ||
      pendingSaves.current.has(bookId)
    )
      return;
    pendingSaves.current.add(bookId);
    const previous = saved.includes(bookId);
    try {
      await updateSavedBook(
        previous,
        (value) => {
          if (!controller.signal.aborted)
            setSaved((current) =>
              value
                ? current.includes(bookId)
                  ? current
                  : [...current, bookId]
                : current.filter((id) => id !== bookId),
            );
        },
        async (next) => {
          await updateLibrary(
            await accessToken(controller),
            bookId,
            next,
            controller.signal,
          );
          if (!controller.signal.aborted)
            telemetry.current?.track(
              next ? "BOOK_SAVED" : "BOOK_UNSAVED",
              bookId,
            );
        },
      );
    } catch (cause) {
      report(cause, controller);
    } finally {
      pendingSaves.current.delete(bookId);
    }
  }
  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="light" />
      <View style={styles.header}>
        <Text style={styles.brand}>BookRush</Text>
        <Pressable onPress={onLogout}>
          <Text style={styles.logout}>Sair</Text>
        </Pressable>
      </View>
      <View style={styles.hero}>
        <Text style={styles.kicker}>DESCUBRA SUA PRÓXIMA LEITURA</Text>
        <Text style={styles.heading}>
          Histórias que <Text style={styles.italic}>ficam</Text>.
        </Text>
      </View>
      <View style={styles.search}>
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Buscar por título…"
          placeholderTextColor="#89978e"
          style={styles.input}
          onSubmitEditing={() => void loadBooks()}
        />
        <Pressable onPress={() => void loadBooks()}>
          <Text style={styles.searchAction}>Buscar</Text>
        </Pressable>
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {busy ? (
        <ActivityIndicator color="#d76c51" style={styles.loader} />
      ) : null}
      <FlatList
        data={books}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        renderItem={({ item, index }) => (
          <BookCard
            book={item}
            index={index}
            saved={saved.includes(item.id)}
            onSave={() => void save(item.id)}
            onOpen={() => {
              setSelected(item);
              telemetry.current?.track("BOOK_OPEN", item.id);
            }}
          />
        )}
        ListEmptyComponent={
          !busy ? (
            <Text style={styles.empty}>
              Pesquise ou atualize para carregar o catálogo.
            </Text>
          ) : null
        }
      />
      {selected ? (
        <View style={styles.modal}>
          <Pressable
            style={styles.close}
            onPress={() => setSelected(undefined)}
          >
            <Text style={styles.closeText}>×</Text>
          </Pressable>
          <Text style={styles.modalTitle}>{selected.canonicalTitle}</Text>
          <Text style={styles.meta}>
            {selected.firstPublicationYear ?? "Ano não informado"} ·{" "}
            {selected.originalLanguage ?? "Idioma não informado"}
          </Text>
          <Text style={styles.modalBody}>
            {selected.description ??
              "Detalhes e trechos deste livro estarão disponíveis em breve."}
          </Text>
          <Pressable
            style={styles.primary}
            onPress={() => setSelected(undefined)}
          >
            <Text style={styles.primaryText}>Continuar explorando</Text>
          </Pressable>
        </View>
      ) : null}
    </SafeAreaView>
  );
}
function BookCard({
  book,
  index,
  saved,
  onSave,
  onOpen,
}: {
  book: Book;
  index: number;
  saved: boolean;
  onSave: () => void;
  onOpen: () => void;
}) {
  const colors = ["#233d35", "#202b52", "#5a2337", "#26311f"];
  return (
    <View
      style={[styles.card, { backgroundColor: colors[index % colors.length] }]}
    >
      <Pressable onPress={onOpen} style={styles.feedPress}>
        <Text style={styles.coverLanguage}>
          {book.originalLanguage?.toUpperCase() ?? "BOOKRUSH"} · TRECHO
        </Text>
        <Text style={styles.feedQuote}>
          “{book.description || `Descubra ${book.canonicalTitle}.`}
        </Text>
        <Text style={styles.cardTitle}>{book.canonicalTitle}</Text>
      </Pressable>
      <View style={styles.cardFooter}>
        <Pressable onPress={onSave}>
          <Text style={styles.star}>{saved ? "★" : "☆"}</Text>
          <Text style={styles.actionLabel}>{saved ? "Salvo" : "Salvar"}</Text>
        </Pressable>
        <Pressable onPress={onOpen}>
          <Text style={styles.star}>↗</Text>
          <Text style={styles.actionLabel}>Abrir</Text>
        </Pressable>
      </View>
    </View>
  );
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#0a0a0d" },
  center: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 28,
    backgroundColor: "#0a0a0d",
  },
  logo: {
    backgroundColor: "#25e0d4",
    color: "#0a0a0d",
    borderRadius: 18,
    paddingHorizontal: 21,
    paddingVertical: 10,
    fontSize: 42,
    fontWeight: "700",
    marginBottom: 24,
  },
  kicker: {
    color: "#9ba1ae",
    fontSize: 11,
    letterSpacing: 1.5,
    fontWeight: "600",
  },
  title: {
    color: "#f3f4f5",
    fontSize: 38,
    lineHeight: 42,
    fontWeight: "700",
    textAlign: "center",
    marginVertical: 14,
  },
  subtitle: {
    color: "#9ba1ae",
    fontSize: 16,
    textAlign: "center",
    marginBottom: 24,
  },
  primary: {
    backgroundColor: "#153d35",
    borderRadius: 28,
    paddingHorizontal: 20,
    paddingVertical: 14,
    alignItems: "center",
  },
  primaryText: { color: "#fff", fontWeight: "700" },
  register: { color: "#356157", marginTop: 16 },
  registerStrong: { color: "#d76c51", fontWeight: "700" },
  hint: { color: "#94a098", fontSize: 11, marginTop: 14 },
  error: {
    color: "#913f31",
    backgroundColor: "#fae8e2",
    padding: 10,
    borderRadius: 8,
    marginVertical: 10,
    textAlign: "center",
  },
  header: {
    height: 64,
    borderBottomWidth: 1,
    borderBottomColor: "#272b33",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 18,
  },
  brand: { color: "#f3f4f5", fontSize: 24, fontWeight: "700" },
  logout: { color: "#356157" },
  hero: { padding: 25 },
  heading: { color: "#f3f4f5", fontSize: 34, fontWeight: "700", marginTop: 10 },
  italic: { color: "#d76c51", fontStyle: "italic" },
  search: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#15171d",
    marginHorizontal: 18,
    borderRadius: 12,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: "#2b2f38",
  },
  input: { flex: 1, height: 46, color: "#f3f4f5" },
  searchAction: { color: "#244e42", fontWeight: "600" },
  loader: { margin: 18 },
  list: { padding: 14, paddingBottom: 30 },
  columns: { gap: 14 },
  card: {
    minHeight: 500,
    borderRadius: 20,
    marginBottom: 14,
    padding: 22,
    justifyContent: "space-between",
  },
  feedPress: { flex: 1 },
  feedQuote: {
    color: "#f7f7f5",
    fontSize: 27,
    lineHeight: 34,
    fontWeight: "700",
    marginTop: 24,
  },
  actionLabel: { color: "#b4bac6", fontSize: 10, textAlign: "center" },
  cover: {
    aspectRatio: 0.72,
    borderRadius: 7,
    padding: 13,
    justifyContent: "space-between",
  },
  symbol: { alignSelf: "flex-end", color: "#fff", fontSize: 30 },
  coverTitle: { color: "#fff", fontSize: 18, fontWeight: "700" },
  coverLanguage: { color: "#fff", fontSize: 10, letterSpacing: 1 },
  cardFooter: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    marginTop: 8,
  },
  cardTitle: {
    flex: 1,
    color: "#fff",
    fontWeight: "800",
    fontSize: 15,
    marginTop: 28,
  },
  star: { color: "#d76c51", fontSize: 25 },
  empty: { color: "#9ba1ae", textAlign: "center", marginTop: 60 },
  modal: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: "#fbfbf7",
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 26,
    shadowColor: "#17352e",
    shadowOpacity: 0.2,
    shadowRadius: 20,
  },
  close: { position: "absolute", right: 18, top: 10 },
  closeText: { fontSize: 30, color: "#718078" },
  modalTitle: {
    fontSize: 28,
    fontWeight: "700",
    color: "#263a32",
    marginTop: 18,
  },
  meta: { color: "#7d8c83", marginVertical: 8 },
  modalBody: { color: "#9ba1ae", lineHeight: 23, marginBottom: 18 },
});

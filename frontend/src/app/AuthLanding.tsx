import type { AuthState } from "../auth";
import { authClient } from "../auth";

type AuthLandingProps = {
  auth: AuthState;
  onLogin: () => void;
};

export function AuthLanding({ auth, onLogin }: AuthLandingProps) {
  return (
    <main className="landing">
      <div className="landing-art">
        <span className="brand-mark large">B</span>
        <div className="floating-word">
          READ
          <br />
          <em>MORE</em>
        </div>
      </div>
      <span className="eyebrow">UM UNIVERSO DE HISTÓRIAS</span>
      <h1>
        Seu próximo
        <br />
        <em>capítulo</em> começa aqui.
      </h1>
      <p>Descubra trechos, livros e leitores que combinam com você.</p>
      {auth.status === "error" && (
        <div className="notice error">{auth.error}</div>
      )}
      <button className="primary-button" onClick={onLogin}>
        Entrar com Keycloak <span>→</span>
      </button>
      <a className="register-link" href={authClient.registrationUrl()}>
        Ainda não tem conta? <strong>Cadastre-se</strong>
      </a>
      <small>Login seguro · Seus dados ficam protegidos</small>
    </main>
  );
}

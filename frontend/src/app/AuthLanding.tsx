import type { AuthState } from "../auth";
import { authClient } from "../auth";
import plantMascot from "../assets/planta/estagio-5.png";
import { Button } from "../components/ui/Button";
import { Logo } from "../components/ui/Logo";
import "./auth-landing.css";

type AuthLandingProps = {
  auth: AuthState;
  onLogin: () => void;
};

export function AuthLanding({ auth, onLogin }: AuthLandingProps) {
  return (
    <main className="auth-landing">
      <header className="auth-landing__header">
        <Logo size={48} alt="" />
        <span>BookRush</span>
      </header>
      <div className="auth-landing__hero">
        <section className="auth-landing__content" aria-labelledby="landing-title">
          <span className="auth-landing__eyebrow">UM UNIVERSO DE HISTÓRIAS</span>
          <h1 id="landing-title">Seu próximo capítulo começa aqui.</h1>
          <p>Descubra trechos, livros e leitores que combinam com você. Cultive seu hábito de leitura a cada nova história.</p>
          {auth.status === "error" && (
            <div className="notice error" role="alert">{auth.error}</div>
          )}
          <div className="auth-landing__actions">
            <Button size="lg" onClick={onLogin}>Entrar <span aria-hidden="true">→</span></Button>
            <a className="br-button br-button--secondary br-button--lg auth-landing__register" href={authClient.registrationUrl()}>Cadastre-se</a>
          </div>
          <p className="auth-landing__security">Login seguro · Seus dados ficam protegidos</p>
        </section>
        <div className="auth-landing__mascot">
          <span className="auth-landing__mascot-label">CULTIVE SUA LEITURA</span>
          <img src={plantMascot} alt="Mascote BookRush: planta de leitura no estágio máximo" />
        </div>
      </div>
    </main>
  );
}

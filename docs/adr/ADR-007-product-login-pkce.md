# ADR-007 — Login do produto com Authorization Code + PKCE

Status: Accepted  
Date: 2026-09-14

## Context

O frontend BookRush é uma SPA React/Vite sem sessão de servidor. O Keycloak já é o provedor de identidade do ambiente e deve continuar controlando credenciais, sessões, MFA e recuperação de senha.

## Decision

O frontend usará um cliente público Keycloak dedicado ao produto e o fluxo Authorization Code com PKCE S256. Tokens e estado de sessão ficam somente em memória; nenhum access token, refresh token, client secret, código OIDC ou token será gravado em localStorage, sessionStorage, URL persistente ou log. O estado transitório da transação OIDC (`state`, `nonce` e PKCE) pode usar o storage de protocolo da biblioteca até o callback e é removido após sua conclusão. `state`, `nonce` e PKCE serão verificados pela biblioteca OIDC adotada. Redirects e `returnTo` ficam limitados a origens explicitamente configuradas.

Após reload, a aplicação pode iniciar uma nova autenticação ou usar SSO do provedor sem criar loop. 401 limpa a sessão e solicita autenticação novamente; 403 mantém a sessão e informa falta de permissão. Logout usa o endpoint OIDC do Keycloak, limpa o estado local e impede novo refresh. Um JWT já emitido pode continuar válido até `exp`; não haverá promessa de revogação instantânea sem mecanismo específico.

## Alternatives Considered

* BFF com cookie HttpOnly: não existe BFF no checkout e adicionaria um serviço de sessão sem necessidade.
* Password grant: proibido pelo modelo de segurança e elimina as garantias do fluxo moderno.
* Emissor JWT próprio: duplicaria o Keycloak e criaria gestão de chaves.

## Consequences

O cliente web não possui segredo e a configuração pública pode ser distribuída no bundle. A renovação deve impedir chamadas concorrentes e tratar a indisponibilidade do IdP com falha recuperável. APIs continuam sendo a fronteira de autorização; esconder botões não concede permissão.

## Risks

Sessões em memória exigem nova autenticação após reload quando o SSO não puder ser restaurado. Um cliente público mal configurado com curingas amplos pode permitir redirects indevidos; cada ambiente deve usar allowlists exatas.

## References

* [Arquitetura de autenticação](../auth/README.md)
* [Inventário do baseline](../platform/baseline-inventory.md)

# Ciclo de acesso administrativo

O realm `bookrush-platform` é a fronteira de engenharia e operação. Ele não
aceita cadastro público e não recebe usuários finais do realm `bookrush`.
Os grupos `developers`, `operators` e `platform-admins` recebem somente as
realm roles técnicas correspondentes no arquivo declarativo; cada cliente ainda
precisa mapear explicitamente as permissões que usa.

## MFA e elevação

Contas dos grupos `operators` e `platform-admins` devem ter TOTP/WebAuthn
registrado antes de receber acesso privilegiado. A política TOTP declarada no
realm usa SHA-1 compatível com o Keycloak 25, 6 dígitos e janela de um passo;
uma mudança de política exige novo ensaio de login e comunicação de
reenrolamento. O realm não cria usuários ou fatores no Git.

O acesso administrativo é concedido por grupo e por cliente, com menor
privilégio. `platform-developer` não implica administração do Keycloak, Jenkins,
Backstage ou storage. Elevação temporária/JIT não existe nesta versão; a
concessão e a retirada precisam ser uma operação auditável do time responsável,
com revisão e expiração registrada fora do código.

## Break-glass e revogação

Uma conta de emergência fica fora do SSO recorrente, armazenada no cofre
operacional e protegida por MFA independente. Ela não é seed do realm nem senha
default. O procedimento de recuperação é:

1. restringir a administração à rede de manutenção;
2. usar a credencial break-glass apenas para recuperar o IdP ou remover um
   grant comprometido;
3. registrar operador, motivo, horário e ações;
4. rotacionar a credencial e revisar sessões imediatamente após o uso;
5. reconciliar a configuração declarativa sem apagar usuários ou clients.

Desabilitar uma conta impede novos tokens, mas não revoga instantaneamente um
JWT já emitido. O prazo efetivo é limitado pelo TTL/access token e pela sessão
local de cada aplicação; o ensaio deve medir esse prazo antes de anunciar um
SLO. Logout encerra a sessão do navegador, mas não reescreve tokens já em
trânsito.

## Estado de validação

O JSON do realm declara cadastro fechado, política de OTP e grupos sem
membership de usuários. A autenticação real, teste de fator ausente, retirada
de grant e recuperação break-glass precisam ser executados com Keycloak
descartável e segredo fornecido em runtime. Não foram executados nesta sessão
porque o daemon Docker não está acessível; não há credencial real versionada.

# Identidade etária no Keycloak 25.0.6

O User Profile do realm de produto recebe o atributo `birthdate` pelo
fragmento em `infrastructure/keycloak/age`. O SPI `bookrush-birthdate`
valida presença no contexto REGISTRATION, calendário civil, formato e
data não futura. Outros contextos de autoedição não podem modificar nem
remover uma data existente. O Keycloak mantém o cadastro e as credenciais.
O cadastro mobile usa Code + PKCE, com o callback OIDC já autorizado.

## Instalação e reconciliação isoladas

```bash
docker build -f infrastructure/keycloak/age-provider/Dockerfile -t bookrush/keycloak-age:local .
bash scripts/test-keycloak-age-profile.sh
mvn -f platform/pom.xml -pl bookrush-common-core -am test
```

Em ambiente acadêmico isolado que use o fluxo de configuração existente,
selecionar a imagem por `KEYCLOAK_IMAGE` e habilitar o patch por
`KEYCLOAK_AGE_PROFILE_ENABLED=true`. O reconciliador usa o Admin API
somente no realm `bookrush` e lê o perfil antes de aplicar o fragmento.
Também remove o mapper padrão de nascimento no escopo OIDC `profile`
e habilita eventos administrativos sem representação. Preserva os demais
mappers, atributos, escopos, usuários e credenciais. O atributo não tem um
mapper de emissão. O opt-in vem desativado em `.env.example`.

## Legado e correção

A ausência de data não adiciona ação obrigatória ao login e é classificada
como `MISSING` (`DATA_AUSENTE` na política). Nesta etapa, o usuário
fornece a data ao operador pelo fluxo controlado de regularização. Ainda
não há uma tela de autosserviço para completar a data. A operação é por
issuer e subject e não recria a conta.

O operador autorizado usa `python3 infrastructure/keycloak/reconcile-age-profile.py fill`
para preencher datas ausentes e o subcomando `correct` para correções.
As credenciais administrativas e as URLs `KEYCLOAK_AGE_ADMIN_URL` e
`KEYCLOAK_AGE_PRODUCT_ISSUER` vêm de ambiente protegido. Fornecer por stdin
um objeto JSON por linha com `issuer`, `subject` e `birthdate`. Em
`correct`, incluir `expectedBirthdate`. Uma linha que já está aplicada
é um no-op. Executar por lotes serializados, pois o Admin API não oferece
uma condição atômica sobre a data anterior. Cada alteração efetiva deixa
um evento de atualização de usuário com ator, alvo e instante, sem valores
do atributo. Um erro interrompe o lote. Repetir os itens já aplicados
preserva a data e não repete a atualização.

## Perfil de usuário com data de nascimento

O realm `bookrush` declara o atributo `birthdate` no `userProfile` de
`infrastructure/keycloak/bookrush-realm.json`, no formato `local-date`
(ISO `yyyy-MM-dd`). Ele é editável pelo próprio usuário e pelo administrador.
Sem essa declaração o Keycloak descarta o atributo silenciosamente e a
`AgePolicy` retorna MISSING, deixando a conta em `AGE_PROFILE_INCOMPLETE`.
O `keycloak-config-cli` aplica o perfil depois do boot somente quando o
atributo de realm `userProfileEnabled` está declarado como `"true"`; sem ele
o import do perfil é pulado silenciosamente. O `--import-realm` do Keycloak
não deve receber a chave `userProfile`, por isso os testes descartáveis
importam uma cópia sem ela. O teste de reconcile confirma `birthdate` em
`users/profile` depois da aplicação.

## Contrato para autorização

`AgePolicy.Source.classify(TechnicalIdentity)` aceita a identidade
derivada de um bearer validado e deve ler o estado atual de uma fonte
privilegiada. Não recebe datas do cliente. `AgePolicy` deriva MISSING,
INVALID, MINOR ou ADULT por relógio injetável. Registros preenchidos no
Keycloak são DECLARED. VERIFIED fica reservado para a evidência confiável
de verificação futura. O verificador sintético administrativo e a
proteção transversal das APIs ficam fora deste Bead.

O instante de transição é informação interna de decisão e permite limitar
a validade de uma decisão em cache. Não publicar a data ou o instante
preciso ao consumidor de produto. Ausência de um fato obrigatório por
indisponibilidade deve ser tratada pelo chamador como 503, seguindo
`product-access-v1`. O contrato não depende de nova emissão de JWT
para reconhecer maioridade. O próximo consumidor deve manter a consulta
server-side e tratar proveniência insuficiente como negação da ação.

O rollback preserva os dados e usa o mesmo provedor enquanto o perfil
referenciar seu validador. Antes de voltar à imagem sem a extensão, remover
somente a configuração de validação por alteração planejada do User Profile.
O teste não reinicia nem modifica ambientes compartilhados.

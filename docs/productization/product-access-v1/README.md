# Contrato de autorização de produto v1

Baseline da main inspecionada: `bd257d74a531f26961c340ef40e50fb7e783ec49`.
Este pacote especifica a integração futura dos ADRs 0035 e 0036.

## Inventário

[Rotas por método, caminho e serviço](routes.tsv) contém 251 mapeamentos Spring
da baseline, quatorze rotas comerciais implementadas depois dela e cinco
entradas ainda planejadas. O marcador AFTER_BASELINE e o prefixo dev
identificam os serviços com exposição no gateway local opcional. Cada linha nomeia o
controller, registra realm, audience e identidade atuais e especifica a ação
e a identidade de destino. `public` indica o caminho que Traefik realmente
encaminha ao dono, inclusive caminhos técnicos roteáveis pelo catch-all;
não implica acesso anônimo. `—` indica ausência de rota para aquele serviço.
Para paths sem rewrite, o HTTP público conserva o caminho interno. As
rotas /ingestion e /analytics removem seus prefixos. /api/admin no host de
produto tem prioridade sobre catálogo e pode cair em outro serviço;
os controllers de assets e review do catálogo continuam no inventário.
O Ingress base do Kubernetes só envia /api ao catálogo e / ao frontend.

| Cobertura | Rotas atuais |
|---|---:|
| Catálogo, assets e conteúdo | 39 |
| BFF, estado e recomendações | 42 |
| Social e perfil | 23 |
| Publisher, upload, revisão e callback | 15 |
| Behavior, sinais e métricas | 12 |
| Administração, analytics e ingestão | 120 |

Receitas adicionais de segurança do inventário:

- `restricted` descreve a política v1. A exceção para saúde pública é só
  para status mínimo. OPTIONS CORS e HEAD implícito seguem a política do
  framework e do GET correspondente, respectivamente. Actuator e docs são
  infra, fora dos controllers da matriz. URLs assinadas S3 GET/PUT são
  credenciais temporárias no host de storage, tratadas no Bead de conteúdo
  ou upload. A decisão v1 protege emissão, leitura e promoção.
- Catálogo permite GETs genéricos e download legado na baseline. Thumbnail
  e certos reader-assets têm condições próprias de scope/role. O acesso
  técnico direto continua sujeito à chain, com política de usuário quando
  houver atuação delegada. Em reader-state, chamadas do BFF são distintas
  da exposição direta de /api/v1/reader/plant. Paths de library enviados
  pelo gateway ao reader-state só funcionam quando existe controller
  correspondente. /api/v1/reader/books, sessions e library são roteados
  primeiro ao BFF. Prefixo roteado não implica rota implementada.
- No publisher, `v1_*` mostra as credenciais futuras. No corte, revisão passa
  de /api/v1/publisher/review a /api/v1/admin/publisher/review e de
  /api/v1/publisher/submissions/{id}/review a
  /api/v1/admin/publisher/submissions/{id}/review. O callback permanece
  interno. A autoridade editorial não autoriza autoria. Publicação e
  promoção exigem direito atual do autor, além da autorização técnica ou
  editorial do chamador.
- Social mantém os checks de operador definidos no controller. Behavior usa
  workload de plataforma e scopes separados para sinais e métricas internas.
  Agregados públicos autenticados e rotas operacionais de behavior têm
  autenticação de produto na baseline. A classificação definitiva das rotas
  operacionais, com scopes mínimos, fica no Bead de integração dessas APIs.
  Callbacks legados com segredo aparecem como `shared-secret`.
- Operações de contas restritas têm allowlist por método e caminho. Todo
  acesso a histórico supervisionado exige conta alvo menor, vínculo
  elegível e concessão ativa para o ator, o alvo e o escopo exatos. Apenas
  as cinco entradas atuais e planejadas de sessão e convites próprios dispensam elegibilidade
  ao produto. Convites ainda têm suas precondições de estado e titularidade.

## Transporte e fronteiras

[OpenAPI 3.1](openapi.json) define `POST /api/internal/v1/product-access/decisions`.
O starter valida assinatura, tempo, issuer e audience **da rota** antes de
criar a requisição. `actor` é a tupla opaca issuer/subject obtida do JWT
validado. `authentication` contém clientId, audience efetivamente validada
e scopes autenticados. A credencial bearer da chamada de decisão representa
o workload chamador, distinta do ator humano. O family-service verifica
issuer, audience `bookrush-family`, scope `product-access.evaluate` e
allowlist de workloads por ação/recurso. Payload e cabeçalhos públicos não
estabelecem identidade ou fatos de negócio.

O serviço dono carrega o recurso e seu proprietário da fonte autoritativa;
usa `resource.owner` quando houver sujeito protegido ou ownership aplicável.
Para criação de obra, a identidade do autor vem do token. O contrato não
substitui a verificação transacional de ownership e estado no serviço dono.
IDs apresentados pelo cliente apenas selecionam recursos. Respostas
públicas podem preservar 404 para ocultar existência de recurso alheio.

Família resolve idade e suspensão pelo adaptador de identidade, vínculo,
delegação e supervisão localmente, e consulta entitlement no subscription
quando a ação exigir. Subscription mantém o entitlement do titular e atende
consulta S2S sem resolver família. Ambos mantêm seus próprios bancos. O
endpoint interno de decisão usa somente a política técnica para evitar
recursão no starter. DOB, pagamentos, evidência legal e dados de leitura
ficam fora da requisição e dos motivos de negação.

| Situação | Resposta |
|---|---|
| Token humano ausente, inválido, expirado, issuer ou audience incompatível | API protegida retorna 401 antes da consulta |
| Token válido, scope ou direito insuficiente | API protegida retorna 403. Uma avaliação concluída retorna internamente 200 com allowed=false e reason |
| Workload sem autenticação válida ou sem permissão para a ação | Endpoint de decisão retorna 401 ou 403, respectivamente. A API protegida falha fechada e trata a integração quebrada como 503 |
| Timeout, circuito aberto, estado obrigatório indisponível ou versão incompatível | API protegida retorna 503 com DECISION_UNAVAILABLE. Nenhum allow anterior autoriza a operação |
| Operador ou workload legítimo em rota dedicada | Preserva a política própria de token, scope e papel. Não consulta elegibilidade de leitor, salvo operações delegadas ou regras específicas do autor |

`correlationId` acompanha requisição, resposta e erro. `policyVersion` é a
versão semântica da política aplicada, inicialmente `1`. `factVersions`
carrega versões opacas dos fatos utilizados e `validUntil` limita validade
pela primeira transição conhecida. A autorização é por operação e o MVP
não reutiliza allows entre requisições. Reavaliar no uso sensível e na
transição final de escrita. Expiração de assinatura, revogação de concessão
ou maioridade invalidam a elegibilidade mesmo com JWT válido.
Problemas retornam `status`, `code` e `correlationId`; motivos publicados
ao cliente podem ser generalizados quando revelariam vínculo ou idade.

## Verificação do contrato

Execute `python3 scripts/tests/test_product_access_contract.py` com
`jsonschema` disponível. O teste confronta o conjunto literal
método/caminho/serviço dos controllers com o TSV e executa os vetores de
política ligados a rotas reais ou planejadas. Também valida schemas de
requisição, decisão e erro. Para comparar com a revisão isolada da main,
use `BOOKRUSH_CONTRACT_SOURCE` apontando para o checkout dessa revisão.

Os Beads seguintes definem SLOs de frescor e timeout, método de verificação
etária, detalhes de retenção, escopos Keycloak por cliente, comportamento
de URLs já assinadas e a transição editorial com conteúdo em processamento.
O [plano principal](../premium-family.md) mantém o grafo e os donos dessas decisões.

A [política etária e de supervisão v1](../age-supervision-v1/README.md)
detalha os escopos independentes e os seletores de READING_HISTORY. A
aprovação de lançamento está pendente.

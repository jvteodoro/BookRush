# Política etária e de supervisão v1

Política v1 definida pelo responsável do projeto em 24/11/2026 para o
protótipo acadêmico, com identidades sintéticas e cobrança simulada. O
[registro da decisão](approval-record.md) documenta os limites do aceite.
A [integração com Keycloak](keycloak.md) descreve a configuração e a regularização.
Integra o [contrato de decisão v1](../product-access-v1/README.md).

## Estado derivado

O Keycloak de produto recebe uma data civil ISO 8601 válida, não futura,
obrigatória para cadastro novo. O owner de identidade mantém a proveniência
e a versão da classificação. Para cada ação, uma política confiável e
versionada do servidor define se é necessária verificação e qual evidência
é suficiente. Até aprovação do nível por fluxo, operações que dependem de
idade verificada permanecem fechadas. Suspensão global confirmada precede
todas as regras de acesso. Em seguida, derive a classificação com o relógio
do servidor. No Brasil, a política usa meia-noite em America/Sao_Paulo na
data civil correspondente, e 1º de março para nascimento em 29/02 quando o
ano do 18º aniversário não for bissexto. Essa regra é uma convenção acadêmica. Nunca derive idade de claim antiga ou dias/365.
O JWT autentica a identidade e não congela sua classificação.

| Estado efetivo | Condição e acesso |
|---|---|
| DATA_AUSENTE | Sem data utilizável; sessão mínima e convites próprios. |
| VERIFICACAO_PENDENTE | Data declarada, exigência de verificação não satisfeita ou contestação com indício relevante; sessão mínima e convites. |
| ADULTO_ATIVO | Data/classificação suficiente e corte atingido; produto gratuito. Publicação exige entitlement próprio ou delegação comercial vigente. |
| MENOR_SEM_FAMILIA | Idade suficiente para classificá-lo como menor, sem membership nem convite válido; sessão mínima e convites. |
| MENOR_CONVIDADO | Existem convites próprios válidos, sem membership; sessão mínima e convites. |
| MENOR_AGUARDA_RESPONSAVEL | Membership ativa sem concessão efetiva PRODUCT_ACCESS; sessão mínima e convites. |
| MENOR_SUPERVISIONADO | Membership ativa, benefício comercial vigente e concessão PRODUCT_ACCESS efetiva; produto com salvaguardas, publicação vedada. |
| MENOR_VINCULO_INATIVO | Membership suspensa ou removida, benefício expirado ou último acesso supervisionado revogado; sessão mínima e convites. Causa registrada na transição. |
| MAIORIDADE_ATINGIDA | Transição auditável para ADULTO_ATIVO. É um efeito de domínio, não uma espera por processamento. |
| CONTA_SUSPENSA | Sem produto, convites ou histórico. Recuperação e contestação seguem fluxo do IdP. |

Classificar nesta ordem: suspensão, ausência de data, prova insuficiente,
adulto, membership inativa (inclui benefício expirado ou a revogação da última autorização de acesso previamente ativa), membership ativa
com/sem acesso autorizado, convite pendente, ausência de vínculo. Convite
novo continua visível em MENOR_VINCULO_INATIVO. O evento de maioridade altera
a classificação só quando o atributo etário é suficiente. A regra opera
sobre fatos independentes, e o serviço não persiste esse rótulo como
autoridade de acesso.

| Gatilho | Pré-condição | Efeito e decisão | Falha |
|---|---|---|---|
| Cadastro ou preenchimento legado | Ator autenticado quando aplicável, data válida, revisão vigente | Incrementa versão. Deriva adulto/menor se prova suficiente, senão aguarda verificação | Data inválida não grava, falha de persistência mantém estado anterior |
| Verificação concluída ou contestação julgada | Revisor autorizado, evidência aprovada, versão atual | Confirma ou corrige a classificação e reavalia direitos no próximo pedido | Evidência inconclusiva mantém restrição; dependência indisponível não concede acesso |
| Convite criado, recusado ou expirado | Titular vigente e vaga reservável na criação; destinatário na recusa, relógio no prazo | Marca pendente, recusa ou expira e libera reserva na saída. Nenhum grant legal | Conflito 409, detalhes de convites alheios ocultos, nenhuma escrita parcial |
| Aceite | Destinatário autenticado, convite ativo e família e reserva válidas | Aceite e membership atômicos; menor aguarda responsável salvo concessão já válida. Data ausente/prova pendente continua restrita | 409 se assento/convite deixou de ser válido, 503 se fato essencial indisponível |
| Aprovar supervisão para acesso | Responsável confirmado, menor elegível e grant PRODUCT_ACCESS vigente | Habilita produto apenas com vínculo e benefício comercial ativos. Escopos de dados são avaliados separadamente | Prova insuficiente ou revisão concorrente mantém estado anterior |
| Suspender/remover membership ou perder entitlement | Mudança confirmada ou prazo efetivo atingido | Revoga direito derivado imediatamente. Preserva relação legal e acesso às ferramentas gratuitas quando a concessão legal segue válida | Fato essencial não consultável produz 503. Nunca usar allow anterior |
| Revogar acesso do responsável ou contestar vínculo legal | Responsável titular do grant renuncia, operador autorizado revoga, ou contestação indica risco | Fecha escopos afetados imediatamente. Menor perde uso se faltar outra autorização PRODUCT_ACCESS válida | Escrita malsucedida não declara sucesso; operador pode suspender acesso por canal autorizado |
| Restabelecer vínculo | Novo aceite para remoção ou reativação explícita para suspensão, fatos atuais válidos | Recalcula; grant revogado/expirado nunca ressurge. Para novo vínculo, reconfirmar PRODUCT_ACCESS | Fato ausente ou conflito mantém restrição |
| Atingir 18 anos | Agora >= corte civil em atributo suficiente | Acesso adulto gratuito; nenhuma consulta parental, nem a dados anteriores. Concessões vencem efetivamente no corte; job só materializa auditoria | Falha do job não prolonga grant. Sem fato confiável, acesso supervisionado falha fechado |
| Corrigir ou invalidar data | Revisor separado, versão atual, trilha e notificação apropriada | Recalcula idade e validade; se voltar a menor, grants encerrados por maioridade exigem nova aprovação | Conflito mantém versão atual; indício material confirmado bloqueia concessões até revisão |

## Responsabilidade e grants

- Titular comercial paga, administra cinco vagas adicionais e pode delegar
  publicação por membro adulto. Membership vincula a conta ao benefício.
  Nenhuma das duas relações confere autoridade sobre histórico alheio.
- Responsável verificado é identidade adulta autenticada com vínculo legal
  apurado para uma identidade menor específica. Pode estar fora do grupo
  comercial. Titular algum pode verificar a própria reivindicação. O menor
  é informado sobre o vínculo e os controles em linguagem adequada à idade.
- O responsável cria uma solicitação autenticada, declara a relação,
  seleciona escopos e confirma que aceita as condições. Solicitação e
  confirmação apenas movem de REQUESTED para PENDING_VERIFICATION.
  Aceite familiar do menor só cria membership. O fluxo de verificação
  autorizado julga identidade, prova de relação e vigência para aquele par,
  com critério de evidência aprovado antes do lançamento. Sem prova
  suficiente permanece pendente ou encerra como REJECTED. Autodeclaração,
  sobrenome, endereço, pagamento e acesso à conta do menor são insuficientes.
- O operador autorizado ativa com escopos explícitos, intervalo
  `[effectiveFrom, effectiveUntil)`, versão e referência opaca da decisão.
  Redução e renúncia têm efeito imediato com autenticação forte. Ampliação
  exige revisão e aprovação nova. O menor pode contestar pelo canal de
  suporte. Conflito de autoridade ou indício de risco suspende os grants
  afetados até decisão; suporte comercial não decide relação legal.
- Estados do grant: REQUESTED, PENDING_VERIFICATION, ACTIVE, SUSPENDED,
  REJECTED, REVOKED e EXPIRED. REJECTED, REVOKED e EXPIRED são terminais.
  Ações com versão esperada e idempotência rejeitam conflito sem
  gravação parcial. Auditoria registra identidades opacas, escopos,
  decisão, versão, origem, motivo controlado e instante. Evidência legal
  em custódia segregada conforme política aprovada. Nunca incluir prova
  ou conteúdo consultado no log operacional.

| Escopo independente | Dados e ações |
|---|---|
| PRODUCT_ACCESS | Autoriza uso do produto pelo menor quando houver vínculo comercial elegível; sem permissão de leitura de dados pelo responsável. |
| BOOKS_ACCESSED | Identificador de livro e instante necessário do acesso efetivo. Sem inferir leitura a partir de impressão. |
| READING_ACTIVITY | Métricas consolidadas de atividade/tempo; sem trechos, posição ou histórico de navegação. Controles obrigatórios de tempo terão autorização específica. |
| COMMENTS | Comentários do menor permitidos pela política do social, com data e bookId; sem mensagens privadas nem material removido. |

Ferramentas legais e escopos de acompanhamento autorizados são gratuitos
e independentes de pagamento. A autorização de consulta usa o vínculo legal
vigente, mesmo quando o benefício familiar expira. Nunca se cria conexão
parental apenas por selecionar uma família. Vencimento fecha novas leituras
do produto pelo menor sob a regra comercial atual e mantém as ferramentas
gratuitas acessíveis. A exigência de vínculo comercial para uso geral é uma decisão acadêmica.
Sua adequação deverá ser revisada antes de qualquer lançamento comercial.

## Aplicação do contrato v1

Autenticar primeiro. Para titularidade alheia, ocultar existência conforme
contrato. Fatos indisponíveis geram 503 apenas quando necessários à ação;
não consultar cobrança para sessão mínima ou supervisão. A decisão mínima
só exige autenticação e o estado de conta necessário, sem exigir data,
assinatura ou prova parental. Concessões e classificação são lidas no
servidor, sem alegações de idade, pagamento ou parentesco no request.

| Operação | Ação e condição |
|---|---|
| Sessão mínima / entrada no produto | SESSION_VIEW permite projeção mínima própria; PRODUCT_USE exige adulto elegível ou menor com os três requisitos. ACCOUNT_RESTRICTED ou ACCOUNT_SUSPENDED conforme causa. |
| Consultar / aceitar / recusar convite | INVITE_SELF, resource.owner igual ao destinatário autenticado. Aceite consulta a reserva e o plano atuais; listar/recusar não ganha acesso ao produto. |
| Publicar | PUBLISH_WRITE, adulto e escopo de rota, ownership e direito próprio ou delegado. AGE_INELIGIBLE para menor, ENTITLEMENT_REQUIRED ou DELEGATION_REQUIRED quando aplicável. |
| Livros acessados | SUPERVISE_HISTORY com resource.type READING_HISTORY e resource.id fixo `books-accessed`, definido pelo backend. resource.owner é o alvo validado. Exige BOOKS_ACCESSED. |
| Atividade de leitura | SUPERVISE_HISTORY com READING_HISTORY e resource.id fixo `reading-activity`. Exige READING_ACTIVITY. A projeção é retornada pelo Reader BFF com `scope=READING_ACTIVITY`. Qualquer seletor ausente ou desconhecido é negado. |
| Comentários | SUPERVISE_COMMENTS com resource.type COMMENT e owner validado; exige COMMENTS. |
| Modificar/revogar | Operação administrativa dedicada, autoridade de plataforma `supervision.manage` e trilha. Renúncia ou contestação do usuário usa fluxo próprio autenticado com escopo restrito a si. Nunca reutilizar FAMILY_MANAGE. Dono reavalia escopo e versões na transação. |

Consultas supervisionadas exigem par exato vigente, objetivo e escopo, alvo
menor com idade confiável e janela de dados permitida. Adulto, alvo
indeterminado ou não elegível gera TARGET_NOT_ELIGIBLE; grant ausente,
revogado ou insuficiente gera SUPERVISION_REQUIRED. Uma resposta pública
pode usar motivo genérico ou 404 conforme o contrato. A cada página,
revalidar o par e a versão, sem cache de allow, encerrando a janela no corte
civil ou na vigência do grant. O histórico visível começa no maior valor entre o início do grant daquele
escopo, o limite de 90 dias e o limite de retenção aplicável. A auditoria
de acessos supervisionados tem prazo de 180 dias no protótipo.

## Brasil: exigências e decisões de lançamento

Pesquisa em 24/11/2026. Confirmar o enquadramento pelos recursos reais,
inclusive o contexto social, antes de habilitar menores.

- **Lei 15.211/2025**, em vigor desde 17/03/2026, prevê avaliação etária,
  proteção por padrão, informações independentes da aquisição, ferramentas
  acessíveis e avisos claros de supervisão. Arts. 16–18 incluem controle
  e métricas consolidadas de tempo, privacidade e salvaguardas. Históricos
  granulares de livros e comentários são escolhas de produto, sujeitas à
  necessidade e ao melhor interesse. O art. 24, no capítulo de redes
  sociais, prevê vínculo com conta de responsável para usuários de até
  16 anos e, para os casos descritos no §4º, suspensão e apelação acessível.
  Revisar alcance para BookRush e para identidade restrita pré-vínculo.
  [Texto da lei](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/lei/l15211.htm)
- **Gratuidade.** A orientação oficial do MJSP descreve ferramentas de
  supervisão acessíveis, claras e sem custo. O BookRush deve oferecer
  controles legalmente exigidos, informações e caminho de verificação
  sem assinatura, assento Premium ou pagamento. Verificar com Jurídico
  o conjunto mínimo aplicável, inclusive limites de uso, desativação de
  recomendações e controles de comunicação. [MJSP](https://www.gov.br/mj/pt-br/assuntos/sua-protecao/sedigi/eca-digital/eca-digital-1)
- **Declaração e verificação.** Declaração informa a data segundo o usuário.
  Verificação confere o atributo com mecanismo técnico ou documental de
  alta confiabilidade. Aferição é o processo mais amplo. O art. 9º da lei
  exige verificação confiável a cada acesso e veda autodeclaração para acesso a conteúdo, produto ou serviço impróprio,
  inadequado ou proibido nos termos do dispositivo. O Decreto 12.880/2026,
  arts. 24–25, trata de proporcionalidade, minimização e limites de sinais.
  Para aferição por documento, observar eliminação imediata e irreversível
  nos termos do art. 24 §3º. Nenhuma cópia de documento em Keycloak, eventos
  ou logs. A orientação preliminar e o guia geral da ANPD informam o
  desenho, sujeitos à atualização regulatória. [Decreto](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12880.htm)
  · [ANPD](https://www.gov.br/anpd/pt-br/assuntos/eca-digital)
- **Proteção do dado e correção.** LGPD arts. 6º, 14–16 e 18 exigem
  minimização, melhor interesse, término do tratamento e direitos de
  correção e eliminação nas hipóteses aplicáveis. O interessado deve ter
  canal de correção/contestação autenticado, com protocolo, evidência por
  canal segregado, decisão fundamentada e notificação. Correção material
  invalida classificações e grants afetados. A restrição temporária é
  aplicada ao risco confirmado e não desativa recuperação nem coleta
  evidência por chat. Definir base legal, encarregado, prazo por classe
  de dado e exceções de retenção com Jurídico/Privacidade. [LGPD](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm)
- **Maioridade e responsabilidade.** O Código Civil fixa 18 anos completos.
  A aplicação ao aniversário bissexto e o fluxo de prova da responsabilidade,
  seus documentos, divergência entre responsáveis e validade de decisões
  precisam de revisão específica. A lei de contagem do ano civil é uma
  referência para a proposta de 1º de março. [Código Civil](https://www.planalto.gov.br/ccivil_03/leis/2002/l10406compilada.htm)
  · [Lei 810/1949](https://www.planalto.gov.br/ccivil_03/leis/1930-1949/l810-49.htm)

Retenção é independente de visibilidade. Cada dono guarda seus próprios
dados. Revogação, vencimento e aniversário cortam o acesso devido sem
apagar automaticamente o histórico do titular dos dados. Definir prazo
máximo por finalidade, excluir ou anonimizar no término, incluir projeções,
outbox, cache e expiração de backups e segregar hipóteses legais de
conservação. Sem exportação em massa e sem novas coletas de atividade
até aprovar finalidade, base legal, janela, impacto e critérios de exclusão.
Auditoria não é cópia do histórico e tem retenção própria.

## Migração e rollback

Ativar por coorte com aviso prévio, ação de atualização no IdP, suporte
e monitoramento agregado de cobertura e erros. Em observação pré-corte,
o legado mantém temporariamente o fluxo anterior existente, sem inferência
de maioridade e sem novos direitos que dependam dela. Contas com indício
material de menoridade ou conflito seguem fluxo de proteção e revisão.
No corte explícito da coorte, usuário que ainda não completou cadastro
vai à sessão restrita, sem troca de sub nem eliminação de dados ou sessões.
Reexecutar importação de fatos por versão. Rollback congela novas coortes
e escritas de política, preserva classificações e restrições já efetivas e
mantém caminhos de correção e segurança. Em indisponibilidade, negar
novos acessos dependentes, sem reativar grants por fallback.

## Aceite

[Casos de fronteira](fixtures.tsv) registram entradas e saídas para os
futuros testes de identidade, família e integração. As respostas HTTP
esperadas referem-se à rota protegida. A decisão acadêmica está no [registro de aprovação](approval-record.md).
Os fluxos de verificação etária ou legal usam exclusivamente contas
sintéticas. Nenhuma implantação comercial foi aprovada.

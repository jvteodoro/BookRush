# Dashboard administrativo BookRush V2

O centro de controle contém seis áreas: Business Overview, Operations, Recommendation Intelligence, Content Intelligence, Analytics Factory e Data Quality. Cada área é carregada sob demanda por React Query e apresenta indicadores, sinais determinísticos, gráficos e detalhes expansíveis, nessa ordem.

O KPI principal é leitores semanais únicos com uma sessão `READ_SESSION` que tenha pelo menos 60 segundos ativos. A definição é `significant-read-v1-active-seconds-gte-60-weekly-unique-readers`; não equivale a progresso de 10%. A população padrão é PRODUCT. ADMIN_SEED, TEST e UNKNOWN só podem ser selecionadas quando a origem autenticada permite classificá-las. Dados históricos sem evidência permanecem UNKNOWN.

Freshness separa `lastBusinessActivityAt`, `projectionUpdatedAt` e `processingLagSeconds`. Falta de atividade não significa falha de refresh. A classificação populacional nunca é recebida do payload de evento do cliente. O identificador composto `TechnicalIdentity[...]` permanece em `identity_subject`, mantendo os joins PFV1, e o issuer verificado é persistido separadamente.

Os indicadores que exigem fontes ainda ausentes estão marcados como indisponíveis no [catálogo de 36 painéis](dashboard-panel-catalog.md). Não se interpretam contagens de eventos como qualidade de recomendação nem se atribui causalidade sem experimento válido. Títulos da interface são orientados a perguntas e não exibem IDs de catálogo.

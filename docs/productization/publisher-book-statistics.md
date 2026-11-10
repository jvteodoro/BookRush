# Estatísticas dos livros do publicador

## Fluxo

Publicador no leitor → Ver estatísticas dos meus livros → `/admin/publisher`.
A sessão da console usa a conta de publicação do realm de plataforma. A página lista apenas as submissões dessa conta e oferece seleção de livro e períodos de 7, 30, 90 ou 365 dias, incluindo hoje. Exibe seis totais e uma tabela diária: impressões, aberturas, leituras, curtidas, compartilhamentos e comentários. Leituras contam eventos READ_PROGRESS e BOOK_COMPLETE; não equivalem a leitores únicos nem livros necessariamente concluídos. Agregados são atualizados pelo job existente, não em tempo real.

## Contrato e autorização

- Browser: GET `/api/v1/publisher/submissions` e GET `/api/v1/publisher/submissions/{id}/metrics?days=30` com bearer da conta plataforma (audience bookrush-ingestion).
- Publisher valida dono da submissão e dono do vínculo aprovado em published_work. Um rascunho com catalogBookId arbitrário não concede acesso. Outra conta recebe 404, sem consultar behavior.
- Sem published_work: NOT_LINKED; sem atividade: AVAILABLE/items vazio; falha externa/credencial: 503. A UI não confunde esses estados com zero acessos.
- Publisher → behavior: GET `/api/internal/v1/publisher/metrics/books/{id}?days=30`, token RS256 de plataforma, audience bookrush-behavior, scope bookrush.publisher.metrics.read. O scope de recomendações não dá acesso a esta rota.
- Só os agregados são retornados; não há identidades de leitores. A janela e o livro são filtrados no PostgreSQL. Nenhuma migration foi alterada ou criada.
- Gateway: rota publisher existente em infrastructure/traefik/dynamic.yaml; não foi criada rota pública para a API interna. CORS preserva origens explícitas existentes.

## Implantação

Reconciliar os arquivos declarativos do Keycloak para criar bookrush-publisher-metrics-s2s e seu scope exclusivo. Configurar PUBLISHER_METRICS_CLIENT_SECRET em ambiente protegido; render-config.py injeta o mesmo binding no client. Compose encaminha a variável ao publisher-service. PUBLISHER_METRICS_CLIENT_ID e PUBLISHER_METRICS_TOKEN_URI têm defaults documentados em .env.example. Não incluir segredos em Git.

Builds do publisher-service, behavior-service e admin-web precisam ser implantados juntos para ativar a integração. Não foi realizado deploy, reconcile em produção, Jenkins remoto ou smoke pelo gateway público com sessão real nesta entrega; esses passos dependem do ambiente de destino e da credencial configurada.

## Evidências locais

- Console: build e lint aprovados; 9 testes passaram.
- Leitor: build aprovado e 70 testes passaram.
- Publisher: mvn verify com 10 testes aprovados; teste de transporte prova token S2S, scope, cache e indisponibilidade sem credenciais.
- Behavior: mvn verify com 20 testes aprovados, incluindo 401 sem token e 403 sem scope exclusivo.
- PostgreSQL 16 descartável: 2 testes explícitos PublisherMetricsPostgresIT aprovados, migrations aplicadas/validadas, isolamento de dono, vínculo aprovado, livro e período.
- Chromium: fluxo com API simulada aprovado, verificando totais, seleção de livro/período, ausência de atividade, publicação sem vínculo e recuperação de falha, sempre com bearer.
- Keycloak 25 descartável: ambos os realms importados e discovery OIDC validado. Client, audience, scope e injeção do segredo no render também conferidos.
- Sintaxe Bash, contratos de pipeline e change gate validados antes do commit. Contratos OpenAPI YAML e respostas conferidos.
- Beads continua bloqueado por banco embedded incompleto (.dolt/repo_state.json ausente). Dados preservados; não houve reinicialização forçada nem sincronização de tarefas.

Os testes PostgreSQL podem ser executados explicitamente com TEST_DATABASE_URL apontando para uma base descartável, usuário bookrush_test e senha de fixture test-only-not-for-production, via `mvn -Dtest=PublisherMetricsPostgresIT test` em cada serviço. Não executar contra bases ou volumes de produção.

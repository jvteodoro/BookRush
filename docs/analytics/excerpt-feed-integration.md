# Excerpts no feed do BookRush

**Estado:** integração implementada e habilitada no host de desenvolvimento.
O client S2S foi reconciliado e o feed consulta excerpts quando existem dados
elegíveis.

Este documento registra a situação verificada no checkout em 2 de outubro de 2026 e o trabalho necessário para exibir excerpts dos livros no feed. O Git continua sendo a fonte canônica e o Backstage apenas publica esta documentação.

## O que já existe

O `book-analytics-service` é o único proprietário do schema `analytics`. A pipeline persiste excerpts ligados à versão textual exata (`catalog.book_asset_version`), com texto, hash SHA-256, offsets Unicode em code points e intervalo half-open `[start, end)`. Também há features e, quando disponíveis, ranking heurístico e metadados de modelo.

Os excerpts podem ser consultados por estas rotas internas do serviço:

| Uso | Rota do serviço | Gateway público interno |
| --- | --- | --- |
| Por livro | `GET /api/internal/v1/content-analytics/books/{bookId}/excerpts?offset=0&limit=50` | `/analytics/api/internal/v1/content-analytics/books/{bookId}/excerpts` |
| Por versão textual | `GET /api/internal/v1/content-analytics/asset-versions/{assetVersionId}/excerpts` | `/analytics/api/internal/v1/content-analytics/asset-versions/{assetVersionId}/excerpts` |
| Excerpt individual | `GET /api/internal/v1/content-analytics/excerpts/{excerptId}` | `/analytics/api/internal/v1/content-analytics/excerpts/{excerptId}` |

Essas rotas são internas e exigem a política de confiança do serviço de analytics. Elas não devem ser chamadas diretamente pelo navegador nem expostas como uma API pública de leitura.

## Por que o feed ainda mostra a descrição

O fluxo atual é:

```text
frontend → reader-bff-service → recommendation-service → catalog-service
```

O `recommendation-service` consulta analytics com client credentials quando as
flags estão habilitadas. A chamada deve usar o prefixo `/api/internal`; sem
esse prefixo o serviço responde 404 e o recommendation-service degrada para
descrição. O `reader-bff-service` repassa o campo nullable e o
frontend prioriza `excerpt.text`, depois descrição e por fim o fallback
`Descubra <título> no catálogo BookRush.`. Com as flags padrão (`false`/`0`), o
comportamento anterior continua ativo.

## Trabalho restante

1. **Executar o gate completo com dois livros**, um processado e outro sem analytics.
2. **Monitorar cache, fallback, latência e erros** antes de aumentar a porcentagem.
3. **Validar a linhagem** contra `sourceAssetVersionId`, hash e troca de versão.

## Sequência recomendada de implementação

```text
analytics persistido
    ↓ chamada S2S com escopo mínimo
serviço de recomendações (composição do feed)
    ↓ contrato de feed versionado
reader-bff-service
    ↓ bearer do usuário somente para a API do produto
frontend
```

O token do usuário continua sendo validado nas APIs do produto. A consulta interna ao analytics ocorre servidor a servidor, com uma credencial e uma audiência próprias. O navegador nunca recebe acesso ao endpoint interno de analytics.

## Validação necessária

Antes de ativar a apresentação no ambiente persistente, executar:

```bash
mvn -q -f services/book-analytics-service/pom.xml test
mvn -q -f services/recommendation-service/pom.xml test
mvn -q -f services/reader-bff-service/pom.xml test
cd frontend && npm run build
git diff --check
```

Também é necessário um teste de integração com dois livros: um com excerpts disponíveis e outro sem processamento. O primeiro deve retornar `excerpt.text` e metadados de linhagem; o segundo deve continuar carregando com a descrição/fallback. Deve ser testada a indisponibilidade do analytics, expiração da credencial S2S, cache e troca de versão textual.

## Limitações conhecidas

- A existência de excerpts não significa que todos os livros do catálogo tenham texto processado.
- Features semânticas, NLI e ranking podem estar ausentes no modo offline; o feed não deve inventar score ou transformar ausência em zero.
- O ranker atual é experimental e não foi validado como métrica de qualidade editorial ou comportamento de leitores.
- A feature continua desligada no `.env.example`; no host de desenvolvimento
  está habilitada com rollout de 100%.

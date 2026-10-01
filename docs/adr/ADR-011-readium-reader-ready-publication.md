# ADR-011 — Readium sobre uma publicação reader-ready por capítulos

Status: Accepted  
Date: 2026-10-01

## Context

O catálogo guarda a versão textual normalizada e a projeção histórica de
capítulos. Um EPUB é um arquivo ZIP e não pode ser passado diretamente ao
`WebPubNavigator` sem um parser de publicação no navegador. O leitor também
precisa preservar a linhagem da versão textual e os offsets em code points.

## Decision

`book-content-service` publica um Web Publication Manifest por livro. A ordem
de leitura aponta para recursos HTML de cada capítulo. O serviço consulta o
catálogo para obter a versão textual exata, solicita uma URL de storage de
curta duração por um token dedicado de serviço e recorta o intervalo do
capítulo em code points. O frontend usa `@readium/navigator` e não mantém texto
de exemplo como fallback de produção.

## Consequences

O navegador não recebe credenciais S3 nem precisa conhecer a estrutura do
bucket. A cada recurso HTML há uma chamada controlada ao content-service, que
permite aplicar limites e observabilidade. Livros sem texto normalizado
projetado permanecem indisponíveis até que a ingestão conclua o artefato.

## Security

`X-Content-Service-Token` é aceito somente no endpoint de resolução interna do
catálogo. O valor vem do ambiente protegido e não é entregue ao browser. URLs
assinadas continuam temporárias e a política de direitos do catálogo permanece
a autoridade para distribuição.

## Validation

`npm run build` no frontend, `mvn test` no catálogo e no content-service e
`git diff --check` devem passar antes do build das imagens.

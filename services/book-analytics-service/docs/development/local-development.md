# Desenvolvimento local

Na raiz, a imagem é construída pelo Dockerfile do serviço. O banco deve possuir
as migrations do `catalog-service` antes da migration `analytics`. Variáveis
mínimas: `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` e `SERVER_PORT`.

O serviço pode ser executado sem modelos: `REMOTE_LLM_ENABLED` não faz parte da
fundação e qualquer futura integração remota será desligada por padrão.

O `TextAssetReader` verifica tamanho, SHA-256, UTF-8, NFC e LF antes de produzir
offsets. Offsets devem ser calculados com `codePointCount`, nunca com o tamanho
UTF-16 de `String`; drift de hash encerra a análise com erro explícito.

Com catálogo e SeaweedFS locais, `ANALYTICS_WORKER_ENABLED=true` executa o
baseline persistente: o worker busca itens `PENDING`, gera janelas de sentenças
e features escalares, e atualiza os contadores do job. O I/O S3 não fica dentro
da transação de claim. Providers de embeddings, emoção e LLM ficam desligados
até que seus artefatos versionados sejam configurados.

# API

Adquire RDF, EPUB e a thumbnail de capa do Gutenberg, persiste snapshots RAW e coordena jobs, normalização TXT e chapters.json.

O EPUB SOURCE continua sendo a fonte estrutural do leitor. Durante o
processamento, o OPF/spine define a ordem dos documentos e o NCX ou NAV define
capítulos e relações pai/filho; essa estrutura é materializada em
`chapters.json` com offsets Unicode para busca, analytics e compatibilidade.
O TXT normalizado é um artefato auxiliar e não substitui o XHTML original no
Readium.

Os offsets de `chapters.json` são calculados depois da normalização final
`NFC_LF`. Isso evita que a compactação de espaços e quebras de linha deixe os
intervalos fora do texto persistido durante o reprocessamento de EPUBs.
O serviço reaplica essa normalização ao materializar o TXT e remapeia novamente
os offsets recebidos do extrator antes de escrever `chapters.json`; portanto o
fluxo de processamento duplo permanece seguro para EPUBs já normalizados.

Quando `processAssets=true`, o job baixa `pg{id}.cover.medium.jpg`, grava o
snapshot RAW em `gutenberg/{id}/thumbnail.jpg` e registra no catalog-service um
asset `THUMBNAIL` com papel `COVER`. Para direitos explicitamente identificados
como public domain, o catálogo cria a decisão de distribuição correspondente e
aprova a thumbnail; demais casos permanecem sujeitos à revisão de direitos.

O payload também aceita `reprocess=true`. Esse modo cria uma nova execução para
IDs já concluídos, sem duplicar o livro canônico, e é o caminho suportado para
atualizar assets importados anteriormente.

O status do catálogo Gutenberg prefere uma tentativa ativa e, na ausência dela,
uma tentativa `SUCCEEDED`/`SKIPPED` com livro canônico. Assim, uma tentativa
antiga com falha não faz uma obra já importada voltar a aparecer como disponível.

OIDC Keycloak com audiência bookrush-ingestion e papéis administrativos; não aceitar credenciais no request de ingestão.

O contrato machine-readable está em api/openapi.yaml, exportado de Springdoc. API Catalog o exibe; endpoints não são duplicados nesta página. Atualize o arquivo a partir da mesma revisão do serviço usando backstage/scripts/export-openapi.py. Breaking changes requerem revisão de consumidores; erros HTTP diferenciam autenticação, validação e indisponibilidade.

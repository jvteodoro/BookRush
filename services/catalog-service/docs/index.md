# catalog-service

Mantém obras, edições, autores, identificadores e assets; autoriza distribuição por URL S3 temporária.

Leia [arquitetura](architecture/overview.md), [desenvolvimento](development/local-development.md), [API](api.md) e [runbook](operations/runbook.md).

Documentação transversal detalhada: [handbook](https://github.com/jvteodoro/BookRush/tree/main/docs).

A busca pública aceita título, título original, descrição, autor e subject. A
API executa a busca no PostgreSQL com paginação, ranking textual e fallback de
correspondência parcial; o frontend não baixa o catálogo inteiro para filtrar
no navegador. A migration V16 adiciona os índices GIN/trigram necessários.

Reader clients use `/api/v1/books/{bookId}/reader-assets` to list active assets and request a non-cacheable, temporary download URL for approved content.

## Categorias bibliográficas

GET `/api/v1/books` e `/api/v1/books/{bookId}` (e aliases internos) retornam
`categories: [{id, name, scheme}]`, obtidas das associações existentes
`catalog.book_subject` → `catalog.subject`. Somente subjects ACTIVE são
exibidos, sem duplicar uma categoria atribuída por várias fontes. A classificação
é carregada em uma consulta por página, evitando N+1; a busca já aceita subjects.
Sem classificação, o contrato retorna `[]`; idioma continua em `originalLanguage`
e nunca é usado como categoria. A ingestão Gutenberg já registra subjects com
proveniência; esta alteração não inventa categorias nem reclassifica dados.
A rota pública de metadados continua pública, sem bearer do realm produto.

A projeção de categorias oculta valores técnicos legados: MIME (incluindo parâmetros charset), tipo Gutenberg Text e códigos curtos de classificação Gutenberg/LCC/DDC. Prateleiras Category: recebem legenda sem o prefixo. IDs e associações originais são preservados, sem apagar proveniência ou exigir reimportação. Subjects literários descritivos continuam disponíveis.

A busca aceita filtros opcionais `category`, `author` e `year`, combinados por
AND com `q`. Categoria e autor fazem correspondência parcial sem distinguir
maiúsculas; somente categorias literárias ativas e créditos AUTHOR participam.
O ano é exatamente o apresentado em `publicationYear`: primeiro ano conhecido
da obra ou, se ausente, o menor ano de edição conhecido. Datas de upload não
substituem datas bibliográficas; ausência de ano/autor continua explícita.
A busca livre encontra autores e categorias; não usa o ano bibliográfico.
O filtro `year` e os campos de datas permanecem apenas para compatibilidade da API;
o site não os utiliza nem os apresenta. Paginação e
contagem usam o mesmo predicado SQL, com ordenação estável por relevância/título/ID.
O CORS de listagem/detalhes públicos permite somente GET/OPTIONS das origens
já configuradas em `CATALOG_CORS_ALLOWED_ORIGINS`, sem credenciais nem wildcard.
A autenticação das rotas internas e de conteúdo protegido permanece obrigatória.

## Corte coordenado da distribuição

`BOOKRUSH_PROTECTED_DISTRIBUTION=true` ativa a autorização no catálogo e
content-service e fecha os hosts externos de S3 e Filer no Traefik. O
Nginx encaminha ambos os hosts ao Traefik. A rede interna mantém o S3
disponível aos serviços; mantenha portas diretas, Filer e outros ingressos
de storage fora de redes de usuários. Qualquer CDN, endpoint S3 externo ou
Ingress alternativo da instalação precisa do mesmo corte antes de aceitar
tráfego protegido. Confirme a ausência de ACLs/policies de leitura anônima.

Provisione os clientes confidenciais com
`KEYCLOAK_CONTENT_DISTRIBUTION_CLIENTS_ENABLED=true` e segredos no ambiente
protegido. Configure a origem interna, issuer, JWKS e endpoint de tokens
conforme `.env.example`. Acrescente ao family-service os seguintes pares
na allowlist:

- `bookrush-catalog-product-access-s2s=PRODUCT_USE:CATALOG,PRODUCT_USE:ASSET`
- `bookrush-book-content-product-access-s2s=PRODUCT_USE:BOOK,PRODUCT_USE:ASSET`

As duas rotas bibliográficas do catálogo exigem política por padrão.
Caso a apresentação bibliográfica pública esteja aprovada, a opção
`CATALOG_SECURITY_PUBLIC_BIBLIOGRAPHY=true` libera somente `GET/HEAD` de
`/api/v1/books` e `/api/v1/books/{bookId}`, com o DTO bibliográfico atual.
Leitores de catálogo ainda anônimos precisam dessa decisão de implantação
ou integração própria antes do corte.

URLs de download de leitura e inspeção administrativa passam a ser caminhos
relativos de API com autenticação no respectivo realm. O catálogo entrega
os bytes com `private, no-store`; a rota de thumbnail também entrega o
objeto diretamente. O realm técnico mantém as rotas internas de
processamento e ingestão. O header legado do content-service deixa de
autenticar quando a proteção está ativa.

O corte de rede é necessário porque URLs S3 históricas seguem assinadas até
a expiração. O TTL legado é configurado por `STORAGE_URL_TTL_SECONDS`, com
padrão de 300 segundos e limite de 3600 segundos. Purgue qualquer cache
externo de respostas de leitura ou URLs históricas no corte. Bloqueie os hosts externos primeiro com `BOOKRUSH_STORAGE_EDGE_CLOSED=true` e
valide uma URL emitida antes do corte. Preserve objetos e manifests
históricos no storage privado. Em rollback, mantenha os hosts bloqueados e
as rotas de conteúdo protegidas ou indisponíveis até restaurar uma versão
que reavalie o acesso. Preserve as credenciais internas e os volumes.

A suíte local integrada é
`BOOKRUSH_E2E_CONTENT=1 bash scripts/test-premium-family-e2e.sh`.

O Ingress base do Kubernetes encaminha `/api` apenas ao catálogo. Para uma
instalação que use esse caminho, habilite a mesma política no catálogo com
credenciais do workload e valide o isolamento do storage da instalação
antes do corte. O E2E cobre a topologia Compose com Traefik.

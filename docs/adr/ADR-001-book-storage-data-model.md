# ADR-001 — Modelo bibliográfico e referências de arquivos

Status: implementado em V1–V6; extensões futuras são identificadas explicitamente.
Schema real: [schema.md](../database/schema.md). Épico: `bookrush-br1`.

## Contexto

No início desta decisão, o catálogo não tinha entidades bibliográficas nem migrations. Precisávamos
representar a obra intelectual, suas manifestações e arquivos, com integridade
relacional e suporte a fontes imperfeitas. Um EPUB de Gutenberg não é a obra;
uma tradução não tem necessariamente os mesmos créditos/licença da original.

## Decisão

Usar PostgreSQL 16, Flyway gerenciado pelo BOM do Spring Boot, migrations SQL
imutáveis e Hibernate `validate`. Todas as tabelas novas vivem no schema
`catalog`, de propriedade do catalog-service. Nenhum segundo mecanismo de DDL.
Migrations anteriores aplicadas não são editadas; evolução por nova versão.
UUID v4 gerado na aplicação (ou explícito em fixtures). Não exigir extensão ou
UUIDv7 não disponível na infraestrutura. Timestamps são TIMESTAMPTZ, mapeados
para Instant em Java. Estados são strings com CHECK, Java EnumType.STRING,
sem ordinal e sem enum nativo que complique evolução/correções de fontes.

### Obra, edição e créditos

`book` contém título canônico obrigatório, título original, idioma original,
ano da primeira publicação e descrição opcionais, estado e timestamps. Títulos
não são únicos. Não contém ISBN, publisher ou formato de arquivo. Estado
MERGED exige referência `merged_into_id`; demais estados não a têm. Proibir
self-merge por CHECK; fluxo futuro de merge deve detectar ciclos mais longos.
Não há serviço de merge nesta primeira etapa.

`edition` pertence a uma obra e contém dados editoriais opcionais: título,
subtítulo, idioma, editora, ano, nome da edição e descrição. Não supor uma única
edição por idioma/editora/ano. A chave adicional UNIQUE(id, book_id) permite
FK composta de assets/ingestão para impedir associação a edição de outra obra.

Manter o nome `author`, compreendido como agente intelectual creditado; nenhum
benefício operacional justifica renomear para person/contributor agora. Nome
obrigatório; normalized_name opcional até haver normalização confiável, nunca
unique. Datas de nascimento/morte são anos opcionais, morte >= nascimento quando
ambos conhecidos. `book_author` guarda créditos da obra; `edition_author` guarda
créditos da edição (tradutor, editor, ilustrador etc.). Mesmos roles em ambas:
AUTHOR, EDITOR, TRANSLATOR, ILLUSTRATOR, CONTRIBUTOR. PK(entity, author, role)
e UNIQUE(entity, position), position > 0: ordem total dos créditos, um agente
pode ter papéis diferentes em posições diferentes. Não duplicar tradutor na obra
quando seu trabalho pertence somente a uma edição.

Idiomas: ISO 639-1 lowercase quando houver, senão ISO 639-3 lowercase; ausência
é NULL. Ex.: pt/en e códigos de três letras sem equivalente de duas letras.
Não armazenar região como idioma (`pt-br`). O banco valida formato de 2–3 letras;
normalização semântica e aliases de fontes serão responsabilidade da ingestão.
Anos usam SMALLINT, intervalo -9999..9999 excluindo zero, com convenção histórica
negativa para a.C.; não impor limite baseado no relógio ou ausência de dados.
Essa faixa rejeita erros grosseiros sem proibir livros antigos ou anúncios de
edições futuras. Não garante plausibilidade de toda metadata externa.

### Fontes, licenças e identidades externas (implementadas em V3)

`source` tem code único; base_url opcional. Uma licença é metadata operacional,
não parecer jurídico. Booleans de permissões podem ser NULL (desconhecido).
`edition.license_id` é padrão, `book_asset.license_id` permite informação mais
específica; licença UNKNOWN nunca significa aprovação de redistribuição.

`external_identifier` tem FKs opcionais explícitas para book, edition ou author,
com CHECK exigindo exatamente um alvo. Evitar entity_type/entity_id sem FK.
source_id obrigatório e UNIQUE(source_id, identifier_type, identifier_value)
para a identidade normalizada. Tipos específicos restringem o alvo: ISBN e
Open Library edition pertencem à edição; Open Library work à obra; outros tipos
exigem decisão documentada na migration. IDs de Gutenberg podem resolver edição
e alcançar obra pela FK. ISBN incorreto/conflitante não é forçado no catálogo:
preservar registro original e marcar ingestão para revisão. Não criar unicidade
global entre diferentes provedores sem política de resolução de conflitos.

### Assets, versões e proveniência (V4–V6)

`book_asset` é lógico: book/edition, tipo, role, source/license e estado. Formato
não é finalidade: EPUB/SOURCE difere de EPUB/PUBLIC. COVER/THUMBNAIL são tipos
semânticos; content_type físico descreve JPEG/WebP etc. `book_asset_version`
contém versão positiva, provider conceitual S3, bucket/key, filename/MIME,
size, hash e disponibilidade física. UNIQUE(asset, version_number) e
UNIQUE(provider,bucket,object_key). SHA-256 é texto lowercase com regex de
64 caracteres, indexado, sem unique global. O futuro serviço deve preservar metadados físicos após
publicação; a DDL permite correções, e o estado de disponibilidade pode mudar. Cada objeto físico tem uma
referência proprietária, sem dedup física compartilhada nesta etapa.

Uma versão pendente pode ainda não ter tamanho/hash; AVAILABLE exige ambos.
Por isso status do asset lógico (aprovação/retirada) e status da versão física
(PENDING_UPLOAD/AVAILABLE/MISSING/FAILED/DELETED) não são confundidos. Defaults
não autorizam distribuição. Histórico de versões/transformações deve ser
preservado; delete físico futuro marca estado, sem cascades de histórico.

`source_record` preserva payload pequeno em JSONB e hash, source/external_id,
retrieved_at e múltiplas revisões. Payloads grandes são objetos de storage.
`ingestion_item` conecta execução, source_record e referências de domínio,
mesmo quando falha antes de identificar a obra. `asset_processing` liga versões
input/output e identifica versão do processador. Output pode ser NULL durante
execução/falha. Cada transformação de uma entrada em várias saídas usa registros
por saída no modelo inicial, com correlação no metadata; fan-in complexo exige
evolução explícita. Nenhum campo estrutural principal existe só em JSONB.

### Evolução

Não implementar book_excerpt/embeddings/recommendations agora. Adiar tabela de
capítulos enquanto não houver produtor, registrando chapters JSON como asset.
Offsets futuros precisam se referir a uma versão física do texto e uma unidade
(bytes UTF-8, codepoints etc.) explícita; não apenas à obra mutável.

## Modelo implementado

O [ER completo](../database/schema.md) corresponde às 14 tabelas e inclui
créditos por edição, revisões de origem, tentativas e linhagem.

## Alternativas e consequências

Uma tabela book com ISBN/provider/blob simplifica o primeiro insert, mas impede
múltiplas edições, troca de fonte e histórico físico. Uma tabela por formato
repete constraints/queries e torna derivados novos migrations de domínio.
Asset plano por versão perde identidade lógica; uma tabela de versões custa
um join e torna histórico/latest explícitos. Identidade polimórfica livre
simplifica extensão de tipos, mas não pode impedir dangling references no banco;
FKs explícitas exigem migration para adicionar alvo, mantendo integridade real.

Schema separado delimita ownership e permite iniciar Flyway mesmo com tabelas
não gerenciadas no public; não autoriza assumir tabelas preexistentes em catalog.
Flyway baseline automático fica desligado. Sem migrations reversas destrutivas:
correções por forward migration; recovery por backup/rebuild apenas de banco
isolado. Não instalar soft delete ou audit framework genérico.

Referências: [Spring Boot: inicialização](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
[PostgreSQL 16: constraints](https://www.postgresql.org/docs/16/ddl-constraints.html).

## Refinamentos em V3

Os alvos/formato de cada identificador estão em entities.md. Gutenberg e
Internet Archive identificam publicações digitais da edição; Open Library
WORK referencia book, EDITION referencia edition. ISBN é estruturalmente
validado, mas checksum e confiabilidade são responsabilidade da ingestão
futura. Seeds de licenças mantêm permissões desconhecidas; nomes genéricos não
fornecem informação suficiente para aprovar distribuição. V3 adiciona
license_id nullable a edition sem alterar dados V2 existentes. O vínculo de
licença ao asset foi introduzido em V4.

## Refinamento implementado em V4

Assets lógicos usam ACTIVE/INACTIVE/DELETED; disponibilidade física pertence
à versão (PENDING_UPLOAD/AVAILABLE/MISSING/FAILED/DELETED). Metadados de conteúdo
são obrigatórios em AVAILABLE e opcionais antes da verificação. Versões ganham
updated_at porque seu estado operacional muda. Imutabilidade dos bytes é
contrato do futuro serviço, não trigger SQL. Provider S3 representa um namespace
configurado por instalação, sem acoplar o domínio ao SeaweedFS.

## Capítulos e extensões futuras — decisão final

Não criar book_chapter agora: nenhum produtor define coordenadas de texto,
encoding ou segmentação. chapters JSON já pode ser uma versão de asset JSON
PROCESSING com aresta CHAPTER_SEGMENTATION; isso preserva linhagem sem impor
um modelo de offsets prematuro. Futuro capítulo/trecho deve referenciar a versão
exata do texto, declarar unidade de offset (bytes/codepoints) e limites, e validar
pais no mesmo documento sem ciclos. Trechos terão texto limitado, nunca TXT
integral. Subjects/genres/topics poderão se associar à obra; interações/progresso
à edição ou versão lida. Analytics e embeddings devem referenciar versões e
processadores; nenhuma coluna de score, vetor ou recomendação foi criada.

## Ingestão, processamento e JPA implementados

V5 registra revisões por source/external_id/hash e tentativas únicas por
job/external_id/attempt_number; contadores contam tentativas concluídas, não
livros distintos. V6 modela uma entrada/saída por aresta, output obrigatório
apenas em SUCCEEDED. Autoarestas são rejeitadas, ciclos indiretos são detectados
nas consultas e precisam de coordenação futura para prevenção concorrente.
Campos de payload flexíveis usam JSONB; vínculos centrais possuem FKs.

JPA usa UUIDs de FK como ownership de escrita e relações LAZY read-only, sem
cascades/inversos ilimitados. Isso exige refresh após mudar FK carregada, mas
permite persistir referências sem carregar árvores. Três repositories atendem
consultas reais. Sem implementações paralelas de book_asset no épico de storage.

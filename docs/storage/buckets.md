# Buckets privados

Nomes vêm de storage.buckets; o bootstrap cria ausentes sem apagar existentes.
Não concede ACL pública. Instâncias existentes com policies modificadas precisam
ser auditadas; bootstrap não sobrescreve políticas administrativas.

| Conceito | Produtor / consumidor | Conteúdo | Mutabilidade / retenção |
|---|---|---|---|
| source | administrador/ingestão → processamento | Originais EPUB/PDF/TXT/HTML/imagens | SOURCE não recebe nova versão; manter enquanto necessário à proveniência |
| public | normalização/admin → distribuição autorizada | EPUB/PDF/TXT, capas | Nova versão exige aprovação; manter histórico até remoção explícita |
| processing | pipelines → pipelines | Normalizados ainda privados, JSON, intermediários | Novas versões; limpeza manual após verificar dependências |
| ml | analytics → consumidores internos | Parquet/datasets/modelos | Novas versões; retenção definida por execução/reprodutibilidade |
| raw | aquisição de fontes → replay/staging | snapshots comprimidos, manifests e arquivos `.part` concluídos | privado; reter enquanto jobs/intents estiverem ativos e limpar por reconciliação |

BucketSelector: SOURCE→source; PUBLIC/COVER→public; NORMALIZED/PROCESSING/DERIVED
→processing; ANALYTICS/ML→ml. PUBLIC é finalidade, não ACL; COVER não é distribuído
anonimamente. Todos requerem credenciais ou GET assinado. Processing/ML/source
só obtêm URL pela API administrativa. Não existem regras automáticas de TTL
ou exclusão no storage, evitando remover objetos ainda referenciados.

`raw` é distinto de `source`: RAW preserva a cadeia de custódia antes de existir
um `book_id`, enquanto `source` representa um asset já associado ao catálogo.
O bucket RAW não participa da distribuição pública e seu nome é configurável por
`STORAGE_BUCKET_RAW` (padrão `books-raw`).

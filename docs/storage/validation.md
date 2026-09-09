# Validação executada — 2026-09-09

## Resultados

- bash scripts/test-storage.sh: exit 0, BUILD SUCCESS.
  10 testes unitários + 40 integração = **50 testes**, zero falhas/erros/skips.
- mvn -B -o test em container da imagem de teste, --network none:
  BUILD SUCCESS, 10 unitários, zero falhas/erros/skips.
- bash scripts/test-compose.sh: exit 0; build Java e frontend
  (tsc --noEmit && vite build) passaram. PostgreSQL/Redis/SeaweedFS/API/frontend/
  pgAdmin ficaram saudáveis; frontend → API retornou database=up/status=ok.
- Compose config --quiet, bash -n dos scripts, sh -n do entrypoint, git diff
  --check e comparação dos dois Jenkinsfiles passaram.

A suíte inclui as 34 integrações anteriores de schema (Flyway V1–V6, JPA e oito
queries SQL documentadas), mais StorageIT (4) e AssetRecoveryIT (2).
Novos unitários: keys/buckets/hash/conteúdo/configuração (5), adapter/erros/
métricas (1), compensação após falha de commit incluindo delete falho (2),
além de StatusControllerTest (2).

## Evidência ponta a ponta

S3 real: quatro buckets privados, upload condicional com SHA-256, HEAD, GET
por stream e URL assinada, DELETE idempotente, 403 anônimo e conflito de overwrite.
API: 401 sem token ou token inválido, 403 sem papel, vínculo livro/asset inválido,
aprovação/retirada de PUBLIC, URL no-store, consulta/listagem, delete e métricas.
Persistência: reserva FAILED durável após falha de storage, novas versões com
keys distintas, retry de delete após falha parcial. Falha de commit é injetada
no teste unitário do coordenador, não simulada como pane real do PostgreSQL.

O script copia URL de canário assinada para localhost:porta dinâmica e realiza
GET a partir de container com rede do host, fora da rede privada do storage.
Recria SeaweedFS e compara bytes. Depois faz dump PostgreSQL, restaura em
restore_probe, copia snapshot físico de SeaweedFS parado, restaura em volume
de teste recriado e compara novamente os bytes por GET assinado. Ambos deram OK.
O volume de backup temporário, containers e redes foram removidos; os volumes
da aplicação em uso não foram utilizados. PostgreSQL real segue no volume
bookrush_postgres_data.

## Ajustes encontrados durante validação

Primeira tentativa: mock restubado invocava exceção anterior; corrigido para
doThrow. Segunda: teste MVC herdava STORAGE_ENABLED do ambiente de integração
e tentava inicializar S3 sem MeterRegistry no slice; teste de status agora
desabilita storage explicitamente. Suíte final completa repetida e aprovada.

Após recriação do gateway podem ocorrer respostas transitórias 500/reset antes
de todas as conexões internas aquecerem; a verificação externa usa retries
limitados e compara bytes ao concluir. Readiness master/filer não promete
ausência de falhas transitórias S3. Na aplicação, timeout/retries SDK e falha
sanitizada permitem tentar novamente sem declarar sucesso indevido.

## Limites e como reproduzir

Testes usam dados/credenciais fictícios e containers locais; primeiro build
pode baixar imagens/dependências. Não consultam internet como fonte de livros.
Token do .env local não foi usado nos testes nem incluído no relatório.

Não houve job Jenkins real, commit/push ou deploy. O mesmo script usado na
etapa Jenkins foi executado via Docker do host; para validar Jenkins, publicar
branch, atualizar imagem Jenkins, provisionar SeaweedFS e executar build sem
DEPLOY primeiro. Não houve teste AWS/R2/CDN, proxy HTTPS público ou restore
de produção: seguir migration-to-cloud.md e operations.md em ambiente próprio.
A validação de formato não substitui antivírus; reconciliação periódica e
retenção ainda são procedimentos manuais documentados.

Reprodução:
```bash
bash scripts/test-storage.sh
bash scripts/test-compose.sh
```

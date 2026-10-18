# Incidente JVM: `SIGBUS` no `hsperfdata`

**Data da investigação:** 2026-10-18  
**Ambiente:** produção BookRush, host `minerva`  
**Serviços afetados:** `recommendation-service` e `reader-bff-service`  
**Estado:** causa identificada; mitigação ainda não implantada neste registro

## Resumo executivo

Os dois serviços terminaram com código 139, que corresponde a `SIGBUS`, sem
serem marcados pelo Docker como vítimas de OOM. Os dois dumps da JVM apontam
para o mesmo caminho nativo do HotSpot:

```text
PerfLongVariant::sample()
StatSamplerTask::task()
PeriodicTask::real_time_tick(int)
WatcherThread::run()
```

O endereço inválido acessado pertence ao mapeamento compartilhado de:

```text
/tmp/hsperfdata_bookrush/1
```

Esse arquivo é criado pelo mecanismo interno de telemetria `hsperfdata` da
JVM. A imagem Temurin 21.0.12.1 Alpine usada pelos dois serviços inicia com
`UsePerfData=true` por padrão. O teste isolado com `-XX:-UsePerfData` iniciou a
JVM normalmente.

A evidência é consistente com corrupção, truncamento ou invalidação do mmap do
`hsperfdata`, e não com erro no código do feed, falha de banco ou falta de heap.

## Evidências primárias

### 1. Dumps da JVM

Os arquivos foram extraídos dos containers parados com `docker cp` e mantidos
no host de investigação, fora do Git. Os hashes permitem correlacionar o
relatório com os arquivos brutos caso sejam arquivados pelo operador:

| Serviço | Arquivo bruto | SHA-256 |
| --- | --- | --- |
| recommendation | `hs_err_pid1.log` | `24abb705661716b3f26d0fc9b4f5dae94a6ee25a062b3827036650f5b3cee63b` |
| reader-bff | `hs_err_pid1.log` | `bbb4077f66509b7eaf920305567713078f56883567f5c893373fcb093397a3b3` |

Trechos relevantes dos dois arquivos:

```text
SIGBUS (0x7)
si_code: 2 (BUS_ADRERR)
Problematic frame: PerfLongVariant::sample()+0x1e
Current thread: WatcherThread "VM Periodic Task Thread"
```

Mapeamentos encontrados:

```text
/tmp/hsperfdata_bookrush/1
```

Os dois dumps também registram Temurin `21.0.12.1+1`, Alpine Linux 3.24,
processo Java `/app/app.jar` e o mesmo caminho de amostragem do HotSpot.

### 2. Estado dos containers

No momento da coleta, ambos estavam parados:

```text
recommendation-service: exit=139, OOMKilled=false
reader-bff-service:      exit=139, OOMKilled=false
```

Não havia `tmpfs` nem volume explícito montado em `/tmp` nesses containers.

### 3. Recursos do host

```text
Kernel: Linux 6.18.22-gentoo-dist x86_64
RAM: 62 GiB total, 42 GiB available durante a coleta
Swap: 15 GiB total, 15 GiB available durante a coleta
/tmp: tmpfs 32 GiB, 1% usado
/: 460 GiB, 96% usado, 21 GiB livres
kernel.perf_event_paranoid: 2
```

A utilização elevada do filesystem raiz é um risco operacional independente e
deve ser tratada, mas não explica este evento: o arquivo problemático estava em
`/tmp`, que é um tmpfs com espaço disponível, e os dumps não indicam OOM.

### 4. Imagens e flag padrão

Imagens observadas na coleta:

```text
bookrush/recommendation-service:local
  sha256:7c70a592df3cc0d193f9e496926bdbc50b8432601e1244824c57ec2ad2a51dbf

bookrush/reader-bff-service:local
  sha256:f0dd218786c2c266ab291867ac89ffed7166023d87fed37947a929484710abeb
```

Consulta feita sem iniciar a aplicação:

```text
docker run --rm --entrypoint java \
  bookrush/recommendation-service:local \
  -XX:+PrintFlagsFinal -version

bool UsePerfData = true {product} {default}
```

Teste da mitigação em runtime:

```text
docker run --rm --entrypoint java \
  bookrush/reader-bff-service:local \
  -XX:-UsePerfData -version

openjdk version "21.0.12.1" 2026-08-18 LTS
```

Esse teste comprova que a flag é aceita pela JVM da imagem; não substitui o
teste de estabilidade do serviço conectado ao Compose.

## Análise de causa

### O que ocorreu

O HotSpot mantém uma área mmap em `hsperfdata_<usuário>/<pid>` para expor
contadores de desempenho a ferramentas como `jstat`. O `WatcherThread` coleta
esses contadores periodicamente. Um `SIGBUS` com `BUS_ADRERR` ao acessar uma
área mmap normalmente significa que a página do arquivo mapeado deixou de ter
backing válido, por exemplo após truncamento, substituição ou inconsistência
do arquivo.

Como o frame é idêntico em dois serviços independentes, com a mesma versão de
JVM/base Alpine e o mesmo nome de arquivo `hsperfdata_bookrush/1`, a explicação
mais forte é o caminho comum do runtime, não uma exceção Java específica de um
serviço.

### Hipóteses descartadas ou enfraquecidas

| Hipótese | Evidência | Avaliação |
| --- | --- | --- |
| OOM do container | `OOMKilled=false`; heap residente baixo nos dumps; RAM disponível | descartada como causa primária |
| Falha de PostgreSQL/feed | frame ocorre no `WatcherThread` nativo antes de qualquer stack de negócio | não explica o crash |
| Falha exclusiva do recommendation | reader-bff reproduziu o mesmo frame e o mesmo tipo de mmap | enfraquecida |
| Falta de espaço em `/tmp` | `/tmp` é tmpfs de 32 GiB com 1% usado | enfraquecida |
| Aplicação Java lança exceção não tratada | sinal fatal nativo, sem stack Java de exceção | descartada como causa primária |
| `perf_event_paranoid` bloqueia a JVM | valor 2 é uma política do kernel, mas a falha está no mmap `hsperfdata` | não há evidência de que seja a causa |

### Fatores de risco secundários

O filesystem raiz está 96% cheio e o cache Docker possui grande volume
reclamável. Isso não foi usado como correção automática para preservar dados,
imagens e volumes. Deve ser acompanhado porque pode causar falhas futuras de
build, logs ou persistência, mas não deve ser confundido com a causa deste
`SIGBUS`.

## Mitigação proposta

Aplicar explicitamente nos serviços afetados:

```text
-XX:-UsePerfData
```

A flag desabilita somente a criação/atualização do mmap de `hsperfdata`; não
desabilita métricas da aplicação, logs, Actuator, Micrometer ou os dados de
analytics. Após a alteração, o procedimento de validação deve ser:

1. reconstruir as imagens sem reutilizar o container parado;
2. verificar no processo efetivo que `UsePerfData` está desabilitado;
3. iniciar ambos os serviços no Compose e confirmar healthchecks;
4. observar logs por pelo menos uma janela superior ao tempo anterior do
   crash;
5. executar smoke autenticado do feed e das rotas do reader;
6. acompanhar se os jobs de analytics continuam retomáveis;
7. manter o rollback para as imagens anteriores caso o healthcheck falhe.

Essa mitigação ainda não foi aplicada neste relatório. Portanto, a produção não
deve ser considerada corrigida apenas com base nesta investigação.

## Procedimento de reprodução/diagnóstico futuro

Com acesso autorizado ao host, sem imprimir ambientes ou credenciais:

```bash
docker inspect bookrush-recommendation-service-1 \
  --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}'
docker inspect bookrush-reader-bff-service-1 \
  --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}'

docker cp bookrush-recommendation-service-1:/tmp/hs_err_pid1.log /tmp/recommendation-hs_err.log
docker cp bookrush-reader-bff-service-1:/tmp/hs_err_pid1.log /tmp/reader-bff-hs_err.log

sha256sum /tmp/recommendation-hs_err.log /tmp/reader-bff-hs_err.log
rg -n 'SIGBUS|BUS_ADRERR|PerfLongVariant|WatcherThread|hsperfdata|Process Memory' \
  /tmp/recommendation-hs_err.log /tmp/reader-bff-hs_err.log

docker run --rm --entrypoint java bookrush/recommendation-service:local \
  -XX:+PrintFlagsFinal -version | rg UsePerfData
```

Nunca registrar no relatório a saída completa de `.env`, `Config.Env`, tokens,
cookies ou segredos de clientes. Se os dumps forem arquivados, preservar acesso
restrito e registrar somente o hash no incidente.

## Fontes no repositório e no incidente

- `services/recommendation-service/Dockerfile`: base Temurin 21 Alpine e
  entrypoint Java atual.
- `services/reader-bff-service/Dockerfile`: mesma base e padrão de entrypoint.
- `infrastructure/compose.yaml`: configuração dos dois serviços, healthchecks,
  rede e dependências.
- `docs/operations/service-development-checklist.md`: requisitos de imagem,
  healthcheck, deploy e smoke público.
- `bookrush-vunc` no Beads: histórico sanitizado da investigação e do incidente
  do feed.
- Dumps brutos referenciados pelos hashes acima: capturados de
  `bookrush-recommendation-service-1` e `bookrush-reader-bff-service-1` em
  2026-10-18; não são armazenados no Git por conterem detalhes de runtime do
  host.

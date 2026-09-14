# ADR-003 — Staging DuckDB/Parquet e gerações seladas

Cada arquivo DuckDB possui um lock de escritor e uma geração própria. O writer
cria tabelas tipadas de staging, grava registros parametrizados e publica um
manifesto `BUILDING` por rename atômico. Leitores só aceitam `READY` com checksum
do arquivo; manifestos incompletos não são consumidos.

`OpenLibraryStageJob` materializa uma geração a partir de um snapshot local
(TSV ou TSV gzipado), roteando namespaces de author/work/edition e preservando
deletes como observações rejeitadas. O manifesto registra parser, filtros,
contagens e o SHA-256 do snapshot. Reexecutar a mesma combinação de snapshot,
parser, fonte, limite e filtros devolve o manifesto `READY` existente sem
duplicar linhas; uma combinação diferente precisa de uma nova geração.

O payload bruto continua no RAW quando excede o limite de registro. Parquet é
uma projeção imutável para leitores paralelos, não uma segunda autoridade. A
retomada de gzip reprocessa a stream desde o início e pula partições confirmadas
por manifestos determinísticos; não presume seek em bytes comprimidos.

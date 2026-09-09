# ADR-003 — Staging DuckDB/Parquet e gerações seladas

Cada arquivo DuckDB possui um lock de escritor e uma geração própria. O writer
cria tabelas tipadas de staging, grava registros parametrizados e publica um
manifesto `BUILDING` por rename atômico. Leitores só aceitam `READY` com checksum
do arquivo; manifestos incompletos não são consumidos.

O payload bruto continua no RAW quando excede o limite de registro. Parquet é
uma projeção imutável para leitores paralelos, não uma segunda autoridade. A
retomada de gzip reprocessa a stream desde o início e pula partições confirmadas
por manifestos determinísticos; não presume seek em bytes comprimidos.

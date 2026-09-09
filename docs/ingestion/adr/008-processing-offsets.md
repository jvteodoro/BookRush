# ADR-008 — Processamento determinístico e offsets

`chapters.json` é produzido depois da normalização final e aponta para a versão
exata do texto e seu SHA-256. O contrato usa UTF-8, NFC, LF, offsets em Unicode
code points e intervalos half-open. Emojis e caracteres combinantes não são
medidos em bytes nem em unidades UTF-16.

Capítulos explícitos preservam `parent`, `position`, título e href. Quando não há
estrutura segura, um único `DOCUMENT` com confiança `FALLBACK` cobre todo o texto.
Intervals fora do texto, folhas sobrepostas, pais que não contêm filhos e ciclos
são rejeitados. A projeção `book_chapter` é histórica e vinculada à versão
textual; uma nova versão não altera capítulos antigos.

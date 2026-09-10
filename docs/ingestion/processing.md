# Processamento de derivados

O processamento lê uma versão SOURCE disponível e produz artefatos
determinísticos em UTF-8/NFC com quebras LF. O texto normalizado é hashado em
SHA-256 e `chapters.json` usa offsets em pontos Unicode, intervalos half-open e
um capítulo `DOCUMENT` quando a fonte não fornece estrutura confiável.

`TextProcessingService` mantém o SOURCE intocado e devolve os bytes de
`normalized.txt` e `chapters.json`; a persistência de versões e da relação
`asset_processing` é feita pelo worker/storage adapter que coordena o job.
Falhas deixam o SOURCE disponível e devem marcar somente a tentativa derivada
como FAILED para retry/reconciliação.

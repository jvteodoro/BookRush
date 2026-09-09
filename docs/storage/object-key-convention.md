# Object keys

Formato real: books/{bookId}/{role-lowercase}/{assetId}/v{number}/{filename}.
UUIDs são internos. O assetId evita colisão entre arquivos do mesmo tipo/nome
numa obra; títulos nunca identificam paths. Número inteiro positivo; filename
de 1–120 caracteres ASCII alfanuméricos, ponto, hífen, underscore, começando
alfanumérico e sem '..'. Separadores e nomes Unicode são rejeitados, não
silenciosamente convertidos. Guardar título/filename de exibição é extensão
futura. ObjectKeyBuilder é o único construtor.

SOURCE não aceita novas versões pela API; upload novo cria asset independente.
PUT usa If-None-Match '*' para recusar sobrescrita. Versões de derivados são
reservadas com lock do asset no PostgreSQL. Novo asset type exige enum/check,
detector de conteúdo, testes e docs; novo role exige também BucketSelector.

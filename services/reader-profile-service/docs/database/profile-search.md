# Dados da pesquisa de pessoas

A migration imutável `V4__profile_search_username.sql` adiciona a coluna nullable
`reader_profile.profile.username`. Não altera identificadores, nomes existentes,
biografias, flags públicas ou preferências de privacidade. Perfis antigos podem
continuar sendo encontrados pelo nome sem preencher username.

O username vem de `preferred_username` do JWT validado na leitura do próprio
perfil. Não pode ser definido por query ou pelo payload de atualização do perfil.
Tokens sem essa claim preservam o username conhecido. O nome é preenchido a
partir de `name`/username somente se ainda estiver vazio; nomes personalizados
são preservados. A sincronização não altera a visibilidade.

A busca usa parâmetros JDBC e `strpos(lower(...), ?)` para tratar `%` e `_`
como texto, sem extensão PostgreSQL ou índice de função incompatível. Como é
substring arbitrária, não há promessa de indexação por prefixo. As consultas
contam e paginam dentro de uma transação read-only/repeatable-read, com limite
de página e quantidade retornada. A ordem usa nome e identificador como desempate.

O teste `scripts/test-profile-search-e2e.sh` valida V1–V3 com um perfil privado
existente, aplica V4 e verifica que o nome e a privacidade foram preservados e
username começa nulo. Também valida boot vazio via Flyway, segundo boot sem
reaplicar a migration e busca real em PostgreSQL16 pelo gateway autenticado.

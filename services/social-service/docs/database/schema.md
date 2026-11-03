# Schema social

O schema `social` armazena curtidas, relações entre leitores, comentários,
compartilhamentos, atividade e denúncias. Comentários mantêm a identidade
técnica em `subject_key` e um snapshot legível em `author_display_name`; o
Admin Console usa o perfil do leitor como fallback para registros antigos.

`social.comment_moderation_state` controla a visibilidade do comentário com os
estados `VISIBLE`, `HIDDEN` e `REMOVED`. `social.reports` referencia livros ou
comentários e registra o ciclo de análise descrito em
[migrations.md](migrations.md). Os identificadores técnicos continuam sendo a
fonte de autorização e auditoria; nomes são apenas dados de apresentação.

`social.comments.author_deleted_at` guarda o instante da remoção pelo próprio
autor; o ator é a identidade imutável `subject_key` da mesma linha. A marca é
independente de `comment_moderation_state` e exclui a linha da leitura pública
mesmo que um estado legado de moderação ainda seja `VISIBLE`.

`social.follows` possui chave primária `(follower, followed)` e índice V9 em
`followed` para contar seguidores recebidos, preservando idempotência de seguir
e deixar de seguir. A privacidade é lida das tabelas já existentes de
`reader_profile` antes de revelar um agregado de outro usuário.

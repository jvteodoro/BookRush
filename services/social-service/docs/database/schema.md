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

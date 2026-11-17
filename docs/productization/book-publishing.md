# Envio de livros próprios

O menu Publicador do leitor abre `/admin/publisher` com a ação **Publicar e
acompanhar meus livros**. A console usa a sessão do realm de plataforma e mantém
as estatísticas dos livros da mesma conta.

O publicador salva um título de até 300 caracteres, retoma o rascunho se necessário,
seleciona EPUB/PDF e envia o arquivo ao staging privado. A interface calcula
SHA-256, normaliza o MIME mesmo quando o navegador não o informa e só libera o
envio à revisão após a finalização. O bearer humano acompanha as chamadas da API,
mas nunca o PUT ao storage. As mensagens distinguem sessão expirada, falta de
permissão, arquivo inválido e falhas temporárias. Arquivo vazio é rejeitado.

A API valida dono e membership ativa. Uma falha de ingestão restaura o rascunho
para UPLOADED quando o callback ainda não confirmou avanço; o dono pode tentar
novamente. Envios repetidos de uma submissão já em processamento não disparam
outra ingestão. Após o sucesso, o livro entra na fila editorial PENDING. A conta
acompanha PROCESSING; ativação do catálogo e aprovação editorial permanecem nas
ações administrativas existentes. O envio não equivale a publicação automática.

As rotas e os requisitos de storage estão em
[API do publicador](../../services/publisher-service/docs/api.md). O storage segue
opt-in (`PUBLISHER_STORAGE_ENABLED=false` no exemplo); habilitar requer credenciais,
URL pública de PUT, CORS da origem real e integração interna com ingestion.
Nenhuma migration nova é necessária.

A validação cobre build/lint e testes das interfaces, Chromium com upload e
submissão falhando e sendo repetidos, SHA-256 e isolamento de bearer; Java cobre
as regras do fluxo e PostgreSQL 16 descartável cobre finalização, isolamento de
dono, retry e fila de revisão com as migrations existentes. Esses testes usam
fixtures para os serviços externos. Deploy, sessão real, configuração S3/CORS e
smoke pelo gateway precisam ser confirmados no ambiente de destino antes de
considerar o fluxo validado em produção.

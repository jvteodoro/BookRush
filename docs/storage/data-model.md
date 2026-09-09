# Persistência

Usamos as entidades e migrations [canônicas](../database/schema.md), sem
duplicar tabela. Asset lógico é criado INACTIVE; versão física PENDING_UPLOAD
é reservada/commitada antes de enviar bytes, com bucket/key/hash/tamanho/MIME.
Sucesso muda versão para AVAILABLE. Falha preserva FAILED ou PENDING_UPLOAD se
o banco estiver indisponível, permitindo recuperação pelo ID/key.

Upload não grava blobs nem URLs. Source/license/edition são FKs existentes.
Hash é SHA-256 calculado no spool; ETag não é usado como hash. Hash repetido não
implica compartilhamento físico. Nenhuma deduplicação física automática.

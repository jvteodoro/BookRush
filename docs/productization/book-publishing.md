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
acompanha PROCESSING; a fila editorial permite ao operador aprovar e ativar o
catálogo. O envio não equivale a publicação automática.

As rotas e os requisitos de storage estão em
[API do publicador](https://github.com/jvteodoro/BookRush/blob/main/services/publisher-service/docs/api.md). O exemplo e o override Jenkins habilitam
`PUBLISHER_STORAGE_ENABLED=true`; o fluxo requer credenciais,
URL pública de PUT, CORS da origem real e integração interna com ingestion.
A migration V18 do catálogo aceita a identidade da submissão.

A validação cobre build/lint e testes das interfaces, Chromium com upload e
submissão falhando e sendo repetidos, SHA-256 e isolamento de bearer; Java cobre
as regras do fluxo e PostgreSQL 16 descartável cobre finalização, isolamento de
dono, retry e fila de revisão. A integração completa também usa os três serviços
e o SeaweedFS reais; o emissor JWT é uma fixture local. Deploy, sessão real, configuração S3/CORS e
smoke pelo gateway precisam ser confirmados no ambiente de destino antes de
considerar o fluxo validado em produção.

## Publicação aprovada e implantação da revisão

A declaração de distribuição é registrada pelo dono antes da revisão. A área de
publicador mostra a fila editorial para operadores; uma conta comum não pode
aprovar. Ao aprovar, publisher chama ingestion com a credencial interna e o
catálogo confirma a identidade canônica da submissão ADMIN_UPLOAD. Só então
copia o SOURCE privado para um asset PUBLIC, registra a decisão de distribuição,
aprova o asset e ativa o livro. Repetir a aprovação preserva o arquivo e os IDs.
A retirada pelo dono inativa o asset público e revoga a aprovação do livro;
o SOURCE é preservado. URLs assinadas já emitidas continuam válidas até expirar.

A migration V18 do catálogo adiciona PUBLISHER_SUBMISSION, antes rejeitado pelas
constraints e pelo enum JPA. O teste aplica V1–V17, preserva um livro existente,
aplica V18 e verifica idempotência e validação do UUID da submissão.

O Jenkins agora constrói, testa, publica e implanta PUBLISHER_IMAGE com a mesma
revisão dos demais serviços. O deploy inclui publisher em preflight, rollback e
diagnóstico e deriva a imagem quando uma definição antiga não fornece a variável.
Um arquivo privado temporário prepara a mesma credencial para publisher e
ingestion (respeita a credencial configurada, ou gera uma nova), sem alterar o
.env protegido nem imprimir segredos. O storage é ativado por padrão nesse
override; uma configuração explícita false permanece respeitada.

`bash scripts/test-publishing-e2e.sh` usa PostgreSQL, SeaweedFS autenticado e os
três serviços reais em rede e volumes descartáveis, sem portas do host ou dados
reais. Apenas o emissor JWT é uma fixture local. Verifica migração fresh/upgrade,
CORS do PUT, assinatura, checksum, isolamento, revisão, download público dos bytes
originais e retirada. A pipeline passa suas imagens versionadas ao teste.

O job deve carregar o Jenkinsfile de `${BRANCH}`. Para instalações que fixaram
sua definição em main, a reconciliação de init.groovy.d precisa ser aplicada no
Jenkins antes de testar a feature. Commits locais ou push na feature não alteram
a configuração persistida de um job que continua fixado em main. Nenhum volume
ou usuário Jenkins deve ser removido para essa reconciliação.

A imagem administrativa também executa os três fluxos Chromium de publicação
(upload e retry, estatísticas, aprovação editorial e retirada) durante o build.
O navegador existe apenas no estágio de validação; o runtime continua Nginx.
O callback de ingestão aceita ausência de `ingestionJobId`, como ocorre no
processamento síncrono do upload, sem impedir o vínculo ao catálogo.

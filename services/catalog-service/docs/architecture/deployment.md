# Deploy

Imagem construída pelo Dockerfile deste serviço. Jenkins chama infrastructure/jenkins/deploy-compose.sh; somente commits na branch remota entram no deploy. Use o healthcheck antes de liberar tráfego. PostgreSQL usa volume persistente; não execute down -v no ambiente compartilhado.

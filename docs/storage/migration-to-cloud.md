# Migração para AWS S3 ou outro compatível

Contrato de domínio e tabelas não mudam: provider S3, bucket/key e hash.
S3ObjectStorage e serviço de assets não precisam conhecer o fornecedor.
Não presumir compatibilidade de operações condicionais/checksums/presign.

1. Criar buckets privados no destino, políticas de menor privilégio e backup.
2. Parar escritores; copiar objetos mantendo keys e metadata SHA-256/MIME.
3. Comparar inventário, tamanho e SHA-256 calculado por GET (não ETag).
4. Ajustar região, credenciais, endpoint público/interno e path-style.
   AWS: omitir endpoint override e usar cadeia padrão de credenciais/IAM role;
   path-style false quando apropriado. R2/outro: endpoint documentado pelo
   fornecedor, região exigida por ele e credenciais próprias.
5. Executar suíte de operações em buckets de teste no destino: conditional PUT,
   HEAD, GET assinado, DELETE, privacidade, checksum e erros.
6. Cortar backend para destino, invalidar/reemitir URLs antigas e observar.
   Manter origem congelada até confirmar corte. Rollback exige reconciliar
   escritas novas; não retornar ao snapshot antigo perdendo uploads.

Se bucket mudar, atualizar referências por migration controlada após verificar
cópia. Schema atual supõe um namespace S3 por instalação; convivência de múltiplas
contas/providers simultâneos exige modelar localização adicional. CDN futura
entra apenas na distribuição pública, sem armazenar URLs no banco. Assinatura
S3 e assinatura CDN são contratos diferentes e exigem implementação própria.

Nenhuma migração cloud/CDN foi executada; não há credenciais cloud neste trabalho.

# ADR-002 — S3-compatible local

Decisão: SeaweedFS OSS em processo único de desenvolvimento (master, volume,
filer e gateway S3), dados em volume Docker, API AWS SDK Java v2 isolada no
adaptador. PostgreSQL mantém asset lógico e versão física, não bytes. Não
implantar CDN, Kubernetes ou cluster distribuído nesta etapa.

BYTEA ampliaria WAL, backups e pressão de memória/conexões para servir binários.
Filesystem direto exigiria protocolo próprio para acesso entre serviços e URLs
temporárias; bind mount também acoplaria serviços ao host. AWS S3 direto oferece
serviço gerenciado, mas depende de conta/rede e não atende testes locais offline.
MinIO oferece API S3, mas não é requisito do usuário; introduzir outro servidor
não reduz a superfície que precisamos validar. Garage privilegia distribuição
de objetos, mas seria outra implementação a qualificar. RustFS é alternativa
S3 que também exige qualificar operações/consistência; não atribuímos paridade
apenas pelo rótulo S3. Ceph envolve operar serviços adicionais e quórum sem
necessidade neste estágio. SeaweedFS atende a escolha solicitada e oferece
separação futura master/filer/volumes; o modo único tem ponto único de falha e
não é alta disponibilidade.

A portabilidade vem do contrato limitado (PUT/HEAD/GET/DELETE/presign GET), não
da suposição de equivalência total entre produtos. Validar novamente checksum,
assinatura, erros e políticas antes de trocar fornecedor. PUT pré-assinado não
é exposto: upload administrativo limitado passa pelo servidor para calcular hash
e validar conteúdo; download grande segue direto por GET assinado.

Referências primárias:
- https://github.com/seaweedfs/seaweedfs/wiki/Amazon-S3-API
- https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html
- https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html

A validação efetiva da versão fixada será registrada em validation.md.

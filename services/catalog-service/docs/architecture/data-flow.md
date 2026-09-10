# Fluxo de dados

Upload valida arquivo e hash, grava objeto S3 e metadados. Downloads usam URL temporária, sem retransmitir o binário pelo Spring. Falhas entre banco e storage exigem compensação/reconciliação.

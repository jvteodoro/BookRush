# Kubernetes

Os manifests usam Kustomize, integrado ao `kubectl` moderno.

## Primeiro deploy

1. Crie um secret real fora do Git a partir do exemplo:

   ```bash
   cp base/db-secret.example.yaml base/db-secret.yaml
   # edite os valores e aplique o arquivo separadamente
   kubectl apply -f base/db-secret.yaml
   ```

2. Aplique a base:

   ```bash
   kubectl apply -k base
   ```

3. Configure um Ingress Controller no cluster (por exemplo, ingress-nginx) e
   ajuste o host em `base/ingress.yaml`.

Em produção, use um gerenciador de segredos (External Secrets, Vault ou o
equivalente do provedor) em vez de manter credenciais em manifestos.

O Jenkins espera os plugins **Pipeline**, **Docker Pipeline**, **Credentials
Binding** e **Git**. Para habilitar deploy, registre um credential do tipo
"Secret file" contendo um kubeconfig e informe seu ID no parâmetro do job.

# Teste de integração do lock de anotação

O fluxo de anotação cega precisa ser testado em três camadas: o cliente HTTP
do admin web, o `book-analytics-service` e o PostgreSQL. O script
`infrastructure/tests/annotation-lock-smoke.sh` executa essa verificação contra
um ambiente Compose já iniciado.

O teste exige três variáveis temporárias, que não devem ser commitadas:

```bash
ANALYTICS_TEST_BEARER='token curto do Keycloak' \
ANNOTATION_ITEM_ID='item reservado para teste' \
ANNOTATION_TEST_SUBJECT='sub do token' \
bash infrastructure/tests/annotation-lock-smoke.sh
```

Ele registra a primeira impressão, bloqueia as seis dimensões, consulta o
PostgreSQL para confirmar as seis linhas `FULL_EXCERPT` do mesmo subject e
repete a operação para verificar o conflito `409 PRIMARY_ALREADY_LOCKED`.
O item usado é mutado; mantenha uma campanha de teste separada da campanha de
produção.

Build e lint continuam sendo verificações complementares. `PREPARE` SQL ou
healthcheck isolados não substituem este smoke test porque não exercitam o
JSON, o controller, a transação JDBC e as constraints em conjunto.

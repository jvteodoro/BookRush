# Test support e limites arquiteturais

`bookrush-test-support` é uma dependência de teste explícita. Ele fornece
apenas JWTs estruturados sem assinatura para testes de conversor/autorização;
eles não ativam bypass em produção e não entram no classpath runtime das
aplicações. Os fixtures distinguem `bookrush` de `bookrush-platform`, evitando
que uma role com o mesmo texto em outro realm seja aceita por engano.

O módulo não contém chaves privadas, senhas ou tokens reais. Testes que validam
assinatura devem usar JWKS local efêmero; o formato `test-only-token` nunca deve
ser aceito por uma configuração produtiva.

A pipeline Jenkins executa ainda um gate de infraestrutura antes das imagens:
`bash -n` dos scripts, `docker compose config` com `.env.example` e
`scripts/validate-pipelines.sh`. Esse gate valida a declaração, não substitui o
smoke com containers descartáveis nem a prova OIDC real.

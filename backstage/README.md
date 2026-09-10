# BookRush Developer Portal

Backstage é o portal oficial de consulta; Git é a fonte canônica.

[Documentação completa](docs/index.md) · [Execução local](docs/local-development.md) ·
[Catálogo](../catalog-info.yaml) · [Configuração](docs/configuration.md)

```bash
cp backstage/.env.example backstage/.env
# Preencha BACKSTAGE_POSTGRES_PASSWORD.
docker compose --env-file backstage/.env -f backstage/compose.yaml up -d --build --wait
```

Em produção, o portal será publicado em
`https://docs-bookrush.jteodoro.tec.br`. Localmente, abra http://localhost:17007.
Sem Docker: Node 24, `cd backstage`, `yarn install --immutable`
e `yarn start`; prepare o ambiente MkDocs conforme a documentação.

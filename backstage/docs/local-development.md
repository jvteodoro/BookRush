# Executar localmente

## Docker (a partir da raiz)

```bash
cp backstage/.env.example backstage/.env
# Preencha BACKSTAGE_POSTGRES_PASSWORD com um valor local gerado.
docker compose --env-file backstage/.env -f backstage/compose.yaml up -d --build --wait
```

Abra http://localhost:17007 e entre como Guest no ambiente local. A porta publica
somente em 127.0.0.1. Num servidor remoto, use túnel SSH; não abra guest na Internet.
A stack é separada da aplicação; não reinicia containers BookRush nem altera .env raiz.
O build requer acesso aos registries npm/PyPI e memória suficiente para webpack.

Em produção, use `https://docs-bookrush.jteodoro.tec.br`; o acesso público deve
passar pelo Nginx e pelo certificado TLS do hostname.

Para instalar o virtual host no servidor, a partir da raiz do checkout:

```bash
sudo bash infrastructure/nginx/install-bookrush-vhosts.sh
```

```bash
docker compose --env-file backstage/.env -f backstage/compose.yaml logs --tail=100 backstage
docker compose --env-file backstage/.env -f backstage/compose.yaml down
```

`down` preserva volumes. `down -v` apaga a base do portal e só deve ser usado em
ambientes descartáveis. Banco dos livros não é montado nem acessado.

## Sem Docker

Pré-requisitos: Node 24, Yarn 4.13.0 (incluído em .yarn/releases), Python 3.12+,
compilador C/C++, make, bibliotecas de build SQLite e Chromium para diagramas.

```bash
cd backstage
node .yarn/releases/yarn-4.13.0.cjs install --immutable
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements-docs.txt
export PATH="$PWD/node_modules/.bin:$PATH"
yarn start
```

Frontend em localhost:3000, backend em localhost:7007. O processo backend roda
em packages/backend; o prestart deriva .local/catalog.yaml do Location raiz e preserva a origem Git de cada entidade.
Use `yarn tsc --noEmit`, `yarn lint:all`, `yarn build:all` e `yarn validate:docs`.

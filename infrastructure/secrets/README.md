# Segredos e recuperação

Segredos não fazem parte do clone. O contrato de runtime é: checkout + Docker
Compose + arquivo de ambiente autorizado (ou segredo decifrado temporariamente)
→ rebuild. Usuários, sessões, bancos e objetos históricos exigem backups
externos e o procedimento de restore abaixo.

## Fonte atual dos segredos

Neste ambiente, o `.env` protegido do servidor é a fonte operacional dos
segredos. Ele não deve ser commitado, copiado para imagens ou incluído em
logs. O bootstrap e a reconciliação recebem esse arquivo explicitamente e
falham quando uma variável obrigatória está ausente.

SOPS + age permanece como evolução futura, quando houver necessidade de
distribuir o ambiente entre máquinas ou armazenar o arquivo cifrado fora do
servidor. Nenhum comando atual exige SOPS.

## SOPS + age (futuro)

O padrão recomendado é SOPS com recipients age. A chave privada fica fora do
Git, fora das imagens e fora dos volumes que serão reconstruídos. O arquivo
`.sops.yaml.example` é apenas um modelo; substitua o recipient por um da equipe
em uma cópia fora do repositório.

```bash
age-keygen -o ~/.config/bookrush/age-key.txt
# publique somente a linha pública age1... para o administrador da configuração
cp infrastructure/secrets/.sops.yaml.example ~/.config/bookrush/.sops.yaml
chmod 700 ~/.config/bookrush && chmod 600 ~/.config/bookrush/age-key.txt
```

Use `scripts/secrets.sh` para cifrar e decifrar. O decrypt escreve um arquivo
temporário `0600`, passa-o ao Compose e o remove no trap; não use `set -x`,
`docker compose config` expandido ou logs contendo esse arquivo.

```bash
SOPS_CONFIG="$HOME/.config/bookrush/.sops.yaml" \
AGE_KEY_FILE="$HOME/.config/bookrush/age-key.txt" \
  bash scripts/secrets.sh encrypt .env .env.enc

SOPS_CONFIG="$HOME/.config/bookrush/.sops.yaml" \
AGE_KEY_FILE="$HOME/.config/bookrush/age-key.txt" \
  bash scripts/secrets.sh run-compose --env-enc .env.enc --profile auth
```

O fluxo SOPS acima ainda não é usado pelo bootstrap atual. Quando for adotado,
deverá manter a mesma separação entre configuração, segredos e dados. Não há
senha default nem geração automática de segredo.

## Rebuild versus restore

* **Rebuild**: recria ambiente vazio a partir do Git e dos segredos autorizados;
  não recupera usuários, sessions, jobs ou objetos.
* **Restore**: restaura backup consistente de PostgreSQL/Keycloak e snapshot
  de storage, verifica checksum e só depois inicia consumidores/reconciliação.

Backups devem ser cifrados, ter retenção, integridade e destino documentados
fora do Git. Não copie um volume de banco ativo enquanto há writers; use dump
consistente ou snapshot coordenado. O restore não promete recuperar sessões
OIDC já emitidas.

Rotacione recipients e segredos separadamente, teste decrypt com uma fixture
sem dados reais e remova arquivos temporários após o uso. Jenkins recebe
segredos por credencial protegida/runtime; não arquiva `.env` ou configuração
expandida.

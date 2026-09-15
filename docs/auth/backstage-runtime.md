# Backstage em runtime público

O bundle estático do Backstage é compilado a partir da configuração base. Em
produção, a configuração OIDC é aplicada pelo overlay de runtime, portanto o
container deve usar `NODE_ENV=production` (o Compose já fornece esse default).

Em hosts públicos, o frontend seleciona o provedor OIDC pelo ambiente de
produção ou pelo hostname não local. Em desenvolvimento local (`localhost` ou
`127.0.0.1`), o provedor guest continua disponível quando o OIDC não está
habilitado.

Após publicar uma nova imagem, faça um hard reload no navegador caso ele ainda
esteja usando um bundle antigo. A validação operacional mínima é:

```bash
docker inspect bookrush-portal-backstage-1 \\
  --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^NODE_ENV='
curl -fsS http://127.0.0.1:17007/ >/dev/null
docker logs --tail 100 bookrush-portal-backstage-1
```

O log não deve mostrar um loop de `GET /api/auth/guest/refresh` em um host
público. Se o DNS público não resolver durante um teste local, valide pela
porta loopback ou pelo hostname configurado no arquivo hosts.

O plugin de documentação de APIs é registrado explicitamente no app e usa sua
fábrica oficial de widgets. A autenticação do portal ocorre no nível das APIs
Backstage; não se deve substituir essa fábrica por um objeto manual, pois isso
causa `api is undefined` durante a inicialização do frontend.

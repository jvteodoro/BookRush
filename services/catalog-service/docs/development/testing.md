# Testes

Na raiz:

```bash
docker build --target build -t bookrush/catalog-service-test services/catalog-service
docker run --rm bookrush/catalog-service-test mvn -B test
bash scripts/test-storage.sh
```

Integração usa ambiente descartável. Fixtures não são seeds de produção. Testes online controlados exigem autorização operacional e ficam fora da CI padrão.

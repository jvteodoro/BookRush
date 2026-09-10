# Testes

Na raiz:

```bash
docker build --target build -t bookrush/book-ingestion-service-test services/book-ingestion-service
docker run --rm bookrush/book-ingestion-service-test mvn -B test
bash scripts/test-ingestion-fixtures.sh
```

Integração usa ambiente descartável. Fixtures não são seeds de produção. Testes online controlados exigem autorização operacional e ficam fora da CI padrão.

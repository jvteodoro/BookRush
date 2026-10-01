.PHONY: analytics-specs analytics-models-fetch analytics-models-verify analytics-models-test analytics-scorer-test analytics-corpus-test analytics-benchmark-test analytics-test
analytics-specs:
	python3 scripts/validate-analytics-specs.py
analytics-models-fetch:
	bash scripts/fetch-analytics-models.sh --fetch

analytics-models-verify:
	bash scripts/fetch-analytics-models.sh --verify

analytics-models-test:
	bash scripts/tests/test-analytics-model-lock.sh
analytics-scorer-test:
	bash scripts/tests/test-candidate-scorer.sh
analytics-corpus-test:
	bash scripts/tests/test-corpus-frequency.sh
analytics-benchmark-test:
	bash scripts/tests/test-analytics-benchmark.sh
analytics-test:
	mvn -q -f services/book-analytics-service/pom.xml test

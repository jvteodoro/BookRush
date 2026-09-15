.PHONY: analytics-specs analytics-models-fetch analytics-test
analytics-specs:
	python3 scripts/validate-analytics-specs.py
analytics-models-fetch:
	bash scripts/fetch-analytics-models.sh
analytics-test:
	mvn -q -f services/book-analytics-service/pom.xml test

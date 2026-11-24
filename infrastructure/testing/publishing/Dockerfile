FROM python:3.12-slim
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openssl && rm -rf /var/lib/apt/lists/*
WORKDIR /tests
COPY scripts/test-publishing-e2e.py /tests/run.py
CMD ["python3", "-u", "run.py"]

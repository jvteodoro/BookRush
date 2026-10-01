FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
RUN useradd --system --create-home analytics
USER analytics
EXPOSE 8092
ENTRYPOINT ["uvicorn","app.main:app","--host=0.0.0.0","--port=8092"]

FROM python:3.11-slim

WORKDIR /app

# 의존성 먼저 설치 (Docker layer cache 활용)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 소스 복사
COPY src/ ./src/
COPY config.yaml .

# 상태 파일 디렉토리 (volume mount 권장)
RUN mkdir -p /data
ENV STATE_DB_PATH=/data/state.db
ENV PYTHONUNBUFFERED=1

CMD ["python", "-m", "src.main"]

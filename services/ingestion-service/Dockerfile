FROM python:3.12-slim AS build
WORKDIR /build
COPY services/ingestion-service/requirements.lock ./
RUN python -m pip wheel --no-cache-dir --require-hashes --wheel-dir /wheels -r requirements.lock
COPY services/ingestion-service/pyproject.toml ./
COPY services/ingestion-service/src ./src
RUN python -m pip wheel --no-cache-dir --no-deps --wheel-dir /wheels .

FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app
WORKDIR /app
RUN addgroup --system rxrelay && adduser --system --ingroup rxrelay rxrelay
COPY services/ingestion-service/requirements.lock ./
COPY --from=build /wheels /wheels
RUN python -m pip install --no-cache-dir --no-index --find-links=/wheels --require-hashes -r requirements.lock \
    && python -m pip install --no-cache-dir --no-index --find-links=/wheels rxrelay-ingestion \
    && rm -rf /wheels
USER rxrelay
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/health/ready', timeout=4)" || exit 1
CMD ["uvicorn", "rxrelay_ingestion.main:app", "--host", "0.0.0.0", "--port", "8090", "--no-access-log"]

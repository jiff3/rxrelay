from fastapi.testclient import TestClient

from rxrelay_ingestion.config import Settings
from rxrelay_ingestion.main import create_app


def test_health_has_correlation_security_headers_and_metrics() -> None:
    application = create_app(Settings(rxnorm_enabled=False))
    with TestClient(application) as client:
        response = client.get("/health/live", headers={"X-Request-Id": "test-request-7"})
        metrics = client.get("/metrics")

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "test-request-7"
    assert response.headers["X-Content-Type-Options"] == "nosniff"
    assert "rxrelay_ingestion_http_requests_total" in metrics.text


def test_unsafe_correlation_id_is_replaced() -> None:
    application = create_app(Settings(rxnorm_enabled=False))
    with TestClient(application) as client:
        response = client.get("/health/live", headers={"X-Request-Id": "bad value"})

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] != "bad value"


def test_production_hides_manual_trigger_and_interactive_docs() -> None:
    application = create_app(
        Settings(environment="production", manual_ingestion_enabled=False, rxnorm_enabled=False)
    )
    with TestClient(application) as client:
        trigger = client.post("/api/v1/ingestions?limit=1")
        docs = client.get("/docs")

    assert trigger.status_code == 404
    assert docs.status_code == 404

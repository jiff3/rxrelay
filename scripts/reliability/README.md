# Reliability Lab scripts

These scripts operate only on local development services. Start core-service with the
`reliability-lab` profile; its publishing and failure-injection endpoints do not exist under the
default profile.

```powershell
$env:SPRING_PROFILES_ACTIVE='reliability-lab'
.\mvnw.cmd -pl services/core-service spring-boot:run
scripts\reliability\duplicate-event.ps1
scripts\reliability\consumer-retry.ps1
scripts\reliability\malformed-event.ps1
```

The checked-in Kafka payload is explicitly synthetic. The duplicate script sends its `event`
member three times. The retry script injects exactly two failures for that event ID. The malformed
script sends one invalid JSON value and lets the normal non-retryable DLQ path handle it.

Run `upstream-timeout.py` from the ingestion virtual environment. It creates a loopback server that
accepts but deliberately does not answer, then verifies the bounded HTTP retry path without calling
FDA. `redis-outage.ps1` stops only the Compose Redis service, calls the authoritative core API, and
always restarts Redis in a `finally` block.

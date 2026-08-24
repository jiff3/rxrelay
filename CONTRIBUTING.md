# Contributing to RxRelay

Thanks for improving RxRelay. Contributions should preserve its central constraints: genuine public source data, explicit provenance, deterministic behavior, and a development stack that fits an 8 GB workstation.

## Start locally

Read [docs/development.md](docs/development.md), copy `.env.example` to `.env` only when overrides are needed, and run:

```bash
docker compose up --build --wait
python scripts/smoke/compose-smoke.py
```

Run `./scripts/verify.sh` on Unix-like systems or `./scripts/verify.ps1` in PowerShell before opening a pull request. The host quality workflow requires Java 21, Python 3.12+, Node 22, and the dependencies installed as described in the development guide.

## Changes

- Open an issue before a major schema, event-contract, or service-boundary change.
- Keep controllers and transport adapters thin; put domain rules in tested services.
- Add a Flyway migration for schema changes. Never edit an applied migration.
- Version incompatible API or event changes. Regenerate and review the TypeScript OpenAPI client.
- Label all synthetic records as fixtures or development events. Never present them as source facts.
- Do not commit credentials, raw bulk datasets, local database volumes, benchmark inventions, or generated dependency/build directories.
- Update affected documentation and add focused tests.

## Pull requests

Explain the problem, design choice, verification commands, and any operational or data-provenance impact. Keep pull requests reviewable and avoid unrelated formatting churn. By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

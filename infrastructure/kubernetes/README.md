# Kubernetes baseline

The base deploys stateless RxRelay workloads and assumes PostgreSQL, Redis, and Kafka services exist in the namespace. For serious environments those stateful dependencies should be managed services or installed with an operator, not a copied single-node manifest.

The workloads use non-root/read-only runtime settings, bounded writable `/tmp` volumes, rollout controls, resource requests/limits, and startup/readiness/liveness probes. Render the base locally before targeting a cluster:

```bash
kubectl kustomize infrastructure/kubernetes
```

Create the required database secret without committing its value, override image names/tags and external service endpoints, then apply:

```bash
kubectl create namespace rxrelay
kubectl -n rxrelay create secret generic rxrelay-secrets --from-literal=database-password='replace-me'
kubectl apply -k infrastructure/kubernetes
```

`secret.example.yaml` documents the keys but is intentionally excluded from `kustomization.yaml`; never apply it unchanged. The optional `openfda-api-key` key may be added to the real Secret. The base intentionally has one application replica and no stateful-service manifests. Choose replica counts only after providing shared external PostgreSQL/Redis/Kafka and verifying consumer/concurrency semantics. Provide network policies, ingress/TLS, authentication, replicated stateful dependencies, and backup/restore policy according to the target platform before Internet exposure. See [deployment.md](../../docs/deployment.md).

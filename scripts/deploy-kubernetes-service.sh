#!/usr/bin/env bash
set -Eeuo pipefail

service="${1:-}"
image="${2:-}"
namespace="${KUBE_NAMESPACE:-bookrush}"
test -n "$service"
test -n "$image"
test -d infrastructure/kubernetes/base

kubectl apply -k infrastructure/kubernetes/base
kubectl -n "$namespace" get deployment "$service" >/dev/null
kubectl -n "$namespace" set image "deployment/$service" "$service=$image"
kubectl -n "$namespace" rollout status "deployment/$service" --timeout=180s

#!/usr/bin/env bash
# =============================================================================
# deploy.sh – Build, push, and deploy the POS Integration app to Kubernetes
# =============================================================================
set -euo pipefail

# ─── Configuration ─────────────────────────────────────────────────────────
IMAGE_REGISTRY="${IMAGE_REGISTRY:-loopdfsregistry.azurecr.io}"
IMAGE_NAME="${IMAGE_NAME:-pos-integration}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo '1.0.0')}"
FULL_IMAGE="${IMAGE_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
NAMESPACE="pos-integration"
K8S_DIR="$(dirname "$0")/k8s"

# ─── Colour helpers ────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ─── Prerequisites check ───────────────────────────────────────────────────
check_prerequisites() {
  info "Checking prerequisites..."
  for cmd in docker kubectl mvn; do
    if ! command -v "$cmd" &>/dev/null; then
      error "$cmd is not installed or not on PATH."
      exit 1
    fi
  done
  info "All prerequisites satisfied."
}

# ─── Build JAR ─────────────────────────────────────────────────────────────
build_jar() {
  info "Building JAR with Maven..."
  mvn clean package -DskipTests -B -q
  info "JAR built: target/pos-integration-1.0.0.jar"
}

# ─── Build Docker image ────────────────────────────────────────────────────
build_image() {
  info "Building Docker image: ${FULL_IMAGE}..."
  docker build \
    --build-arg BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --build-arg VCS_REF="${IMAGE_TAG}" \
    -t "${FULL_IMAGE}" \
    -t "${IMAGE_REGISTRY}/${IMAGE_NAME}:latest" \
    .
  info "Docker image built."
}

# ─── Push Docker image ─────────────────────────────────────────────────────
push_image() {
  info "Pushing image to registry..."
  docker push "${FULL_IMAGE}"
  docker push "${IMAGE_REGISTRY}/${IMAGE_NAME}:latest"
  info "Image pushed."
}

# ─── Apply Kubernetes manifests ────────────────────────────────────────────
deploy_k8s() {
  info "Applying Kubernetes manifests from ${K8S_DIR}..."

  # Apply in order
  for manifest in \
    00-namespace.yaml \
    01-configmap.yaml \
    02-secret.yaml \
    03-mysql.yaml \
    04-deployment.yaml \
    05-service-hpa.yaml \
    06-ingress.yaml; do
      file="${K8S_DIR}/${manifest}"
      if [[ -f "$file" ]]; then
        kubectl apply -f "$file"
        info "Applied: ${manifest}"
      else
        warn "Manifest not found, skipping: ${manifest}"
      fi
  done

  # Patch the deployment image to the exact tag we just built
  kubectl set image deployment/pos-integration \
    pos-integration="${FULL_IMAGE}" \
    -n "${NAMESPACE}"

  info "Image patched to: ${FULL_IMAGE}"
}

# ─── Wait for rollout ──────────────────────────────────────────────────────
wait_rollout() {
  info "Waiting for deployment rollout (timeout: 180s)..."
  if kubectl rollout status deployment/pos-integration \
      -n "${NAMESPACE}" --timeout=180s; then
    info "Rollout completed successfully."
  else
    error "Rollout timed out or failed. Run:"
    echo "  kubectl describe deployment pos-integration -n ${NAMESPACE}"
    echo "  kubectl logs -l app=pos-integration -n ${NAMESPACE} --tail=50"
    exit 1
  fi
}

# ─── Smoke test ────────────────────────────────────────────────────────────
smoke_test() {
  info "Running smoke test via kubectl port-forward..."
  # Forward in the background
  kubectl port-forward svc/pos-integration-service 9090:80 \
    -n "${NAMESPACE}" &>/dev/null &
  PF_PID=$!
  sleep 5

  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:9090/actuator/health 2>/dev/null || echo "000")

  kill $PF_PID 2>/dev/null || true

  if [[ "$STATUS" == "200" ]]; then
    info "Smoke test PASSED (HTTP 200)."
  else
    warn "Smoke test returned HTTP ${STATUS}. Verify manually."
  fi
}

# ─── Main ──────────────────────────────────────────────────────────────────
main() {
  echo "============================================="
  echo " NCBA POS Integration – Deployment Script"
  echo " Image : ${FULL_IMAGE}"
  echo " NS    : ${NAMESPACE}"
  echo "============================================="

  check_prerequisites
  build_jar
  build_image

  if [[ "${SKIP_PUSH:-false}" == "true" ]]; then
    warn "SKIP_PUSH=true – skipping registry push (local cluster assumed)."
  else
    push_image
  fi

  deploy_k8s
  wait_rollout
  smoke_test

  info "Deployment complete!  🎉"
  echo ""
  echo "Useful commands:"
  echo "  kubectl get pods -n ${NAMESPACE}"
  echo "  kubectl logs -l app=pos-integration -n ${NAMESPACE} -f"
  echo "  kubectl port-forward svc/pos-integration-service 8080:80 -n ${NAMESPACE}"
}

main "$@"

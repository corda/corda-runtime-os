#!/usr/bin/env bash
SCRIPT_DIR=$(dirname "$0")
set -o errexit -o pipefail

# Colors
NO_COLOR='\033[0m'
INFO_COLOR='\033[1;32m'
WARN_COLOR='\033[0;31m'

# Install namespace, 'corda' by default
CORDA_NAMESPACE=${CORDA_NAMESPACE:-"corda"}

# Kubernetes Version, 'latest' by default
CLUSTER_VERSION=${CLUSTER_VERSION:-"latest"}

function info() {
  echo -e "${INFO_COLOR}"["$(date '+%F %T')"]: "${1}${NO_COLOR}"
}

function warn() {
  echo -e "${WARN_COLOR}"["$(date '+%F %T')"]: "${1}${NO_COLOR}"
}

function check_script_pre_requisites() {
  command -v helm >/dev/null 2>&1 || { warn "helm must be installed"; exit 1; }
  command -v kubectl >/dev/null 2>&1 || { warn "kubectl must be installed"; exit 1; }
  command -v minikube >/dev/null 2>&1 || { warn "minikube must be installed"; exit 1; }
}

function setUp() {
  info "Creating MiniKube Cluster With Version ${CLUSTER_VERSION}..."
  minikube start --cpus=6 --memory=8G --kubernetes-version="${CLUSTER_VERSION}"
  kubectl cluster-info --context minikube
  eval "$(minikube docker-env)"

  info "Deploying Pre-Requisites..."
  kubectl create namespace "${CORDA_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
  pushd "${SCRIPT_DIR}/../../corda-dev-helm" >>/dev/null
    helm repo add bitnami https://charts.bitnami.com/bitnami
    helm dependency build charts/corda-dev
    helm upgrade --install prereqs --namespace "${CORDA_NAMESPACE}" charts/corda-dev --render-subchart-notes --timeout 10m --wait
  popd >>/dev/null

  info "Pushing Corda Docker Images..."
  pushd "${SCRIPT_DIR}/../../corda-runtime-os" >>/dev/null
    ./gradlew publishOSGiImage --parallel
  popd >>/dev/null

  info "Deploying Corda..."
  pushd "${SCRIPT_DIR}/../../corda-runtime-os" >>/dev/null
    helm install corda --namespace "${CORDA_NAMESPACE}" charts/corda --values values.yaml --wait
  popd >>/dev/null

  info "Setting Up Port Forwarding..."
  set +o errexit
  PID=$(pgrep kubectl port-forward deployment/port-forward corda-rpc-worker)
  [[ -n "${PID}" ]] && kill -9 "${PID}" >>/dev/null
  rm -Rf portforward-*.log >>/dev/null
  set -o errexit
  kubectl port-forward --namespace "${CORDA_NAMESPACE}" deployment/corda-rpc-worker 8888 > ./portforward-rpc-worker.log 2>&1 &

  info "Retrieving Default Corda Credentials..."
  CORDA_USERNAME=$(kubectl --namespace "${CORDA_NAMESPACE}" get secret corda-initial-admin-user -o go-template='{{ .data.username | base64decode }}')
  CORDA_PASSWORD=$(kubectl --namespace "${CORDA_NAMESPACE}" get secret corda-initial-admin-user -o go-template='{{ .data.password | base64decode }}')

  info "Done!. Default Credentials: ${CORDA_USERNAME}, ${CORDA_PASSWORD}. Swagger Endpoint: https://localhost:8888/api/v1/swagger"
}

function tearDown() {
  info "Deleting Minikube Cluster..."
  minikube delete
}

check_script_pre_requisites

case "${1}" in
  create)
    setUp
    ;;
  delete)
    tearDown
    ;;
  *)
    echo "Usage: ${0##*/} [create|delete]"
    ;;
esac

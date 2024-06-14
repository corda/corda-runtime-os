#!/bin/bash

if [ -z "$1" ]; then
  namespace=$(kubectl config view --minify -o jsonpath='{..namespace}')
else
  namespace="$1"
fi

if [ -z "${namespace}" ]; then
      printf "No namespace has been provided.\nPlease set one by either doing:\n  ./support_bundle.sh <namespace>\nor\n  kubectl config set-context --current --namespace=<namespace>\n"
      exit 1
fi

workDir=$(mktemp -d)
namespaceDir="${workDir}/${namespace}"
mkdir "${namespaceDir}"

echo "Collecting events for namespace ${namespace}"
kubectl --namespace "${namespace}" get events --sort-by=lastTimestamp > "${namespaceDir}/kubernetes_events.txt"

echo "Collecting Helm releases"
helmDir="${namespaceDir}/helm"
mkdir "${helmDir}"
helm ls --namespace "${namespace}" > "${helmDir}/ls.txt"

for release in $(helm ls -q --namespace "${namespace}"); do
  echo "Collecting values and manifest for Helm release ${release}"
  releaseDir="${helmDir}/${release}"
  mkdir "${releaseDir}"
  helm get values "${release}" --namespace "${namespace}" > "${releaseDir}/values.txt"
  helm get manifest "${release}" --namespace "${namespace}" > "${releaseDir}/manifest.txt"
done

kubectl proxy >/dev/null 2>&1 &
pid=$!
for podName in $(kubectl --namespace "$namespace" get pods -o jsonpath="{.items[*].metadata.name}"); do
  echo "Collecting configuration and logs for pod ${podName}"
  podDir="${namespaceDir}/${podName}"
  mkdir -p "${podDir}"
  kubectl --namespace "${namespace}" describe pod "${podName}" > "${podDir}/describe.txt"
  kubectl --namespace "${namespace}" logs "${podName}" --all-containers=true --ignore-errors --prefix=true > "${podDir}/logs.txt"
  restartCount=$(kubectl --namespace "$namespace" get pod "${podName}" -o jsonpath="{.status.containerStatuses[0].restartCount}")
  if [[ $restartCount -gt 0 ]]; then
    echo "Pod ${podName} has restarted - collecting previous logs"
    kubectl --namespace "${namespace}" logs "${podName}" --ignore-errors --prefix=true --previous > "${podDir}/previous-logs.txt"
  fi
  if [[ "$podName" == *-worker-* ]]; then
    echo "Collecting status for pod ${podName}"
    curl -s "localhost:8001/api/v1/namespaces/${namespace}/pods/${podName}:7000/proxy/status" -o "${podDir}/status.json"
  fi
done
disown $pid
kill $pid

for restSvcName in $(kubectl get svc --namespace "$namespace" -l app.kubernetes.io/component=rest-worker -o jsonpath="{.items[*].metadata.name}"); do
    instance=$(kubectl get --namespace "$namespace" svc "$restSvcName" -o go-template='{{ index .metadata.labels "app.kubernetes.io/instance" }}')
    if kubectl get secret --namespace "$namespace" "$instance-rest-api-admin" > /dev/null 2>&1; then
      configDir="${namespaceDir}/config/${instance}"
      mkdir -p "$configDir"
      echo "Collecting Corda configuration via service ${restSvcName}"
      username=$(kubectl get secret --namespace "$namespace" "$instance-rest-api-admin" -o go-template='{{ .data.username | base64decode }}')
      password=$(kubectl get secret --namespace "$namespace" "$instance-rest-api-admin" -o go-template='{{ .data.password | base64decode }}')
      kubectl port-forward --namespace "${namespace}" "svc/${restSvcName}" 9443:443 > /dev/null 2>&1 &
      pid=$!
      if curl -sk "https://localhost:9443" --retry 10 --retry-delay 1 --retry-all-errors > /dev/null 2>&1; then
        sections="crypto externalMessaging flow ledger.utxo membership messaging p2p.gateway p2p.linkManager rbac reconciliation rest sandbox secrets security stateManager vnode.datasource"
        for section in $sections; do
            curl -sk -u "${username}:${password}" "https://localhost:9443/api/v5_3/config/corda.${section}" -o "${configDir}/corda.${section}.json"
        done
      fi
      disown $pid
      kill $pid
    else
      echo "Unable to collect Corda configuration via service ${restSvcName} as REST API credentials unavailable"
    fi
done

bundle="${namespace}-support-bundle-$(date +"%Y-%m-%dT%H_%M_%S").tgz"
tar -C "${workDir}" -czvf "${bundle}" "${namespace}"
echo "Created support bundle ${bundle}"

rm -rf "$workDir"

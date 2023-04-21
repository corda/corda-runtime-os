#!/bin/bash

if [ -z "$1" ]; then
  namespace=$(kubectl config view --minify -o jsonpath='{..namespace}')
else
  namespace="$1"
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
    kubectl port-forward --namespace "${namespace}" "${podName}" 7000:7000  >/dev/null 2>&1 &
    pid=$!
    curl -s localhost:7000/status -o "${podDir}/status.json"
    disown $pid
    kill $pid
  fi
done

bundle="${namespace}-support-bundle-$(date +"%Y-%m-%dT%H_%M_%S").tgz"
tar -C "${workDir}" -czvf "${bundle}" "${namespace}"
echo "Created support bundle ${bundle}"

rm -rf "$workDir"

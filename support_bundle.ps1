#!/usr/local/bin/pwsh

param ($namespace=$(kubectl config view --minify -o jsonpath='{..namespace}'))

$parent = [System.IO.Path]::GetTempPath()
[string] $name = [System.Guid]::NewGuid()
$workDir = Join-Path $parent $name

mkdir $workDir
$namespaceDir = Join-Path $workDir $namespace
mkdir $namespaceDir

Write-Output "Collecting events for namespace ${namespace}"
kubectl --namespace "${namespace}" get events --sort-by=lastTimestamp > (Join-Path $namespaceDir "kubernetes_events.txt")

Write-Output "Collecting Helm releases"
$helmDir = Join-Path $namespaceDir "helm"
mkdir $helmDir
helm ls --namespace "${namespace}" > (Join-Path $helmDir "ls.txt")

foreach ($release in $(helm ls -q --namespace "${namespace}").Split([System.Environment]::NewLine))
{
  Write-Output "Collecting values and manifest for Helm release ${release}"
  $releaseDir = Join-Path $helmDir $release
  mkdir $releaseDir
  helm get values "${release}" --namespace "${namespace}" > (Join-Path $releaseDir "values.txt")
  helm get manifest "${release}" --namespace "${namespace}" > (Join-Path $releaseDir "manifest.txt")
}

foreach ($podName in $(kubectl --namespace "$namespace" get pods -o jsonpath="{.items[*].metadata.name}").Split(" "))
{
  Write-Output "Collecting configuration and logs for pod ${podName}"
  $podDir = Join-Path $namespaceDir $podName
  mkdir $podDir
  kubectl --namespace "${namespace}" describe pod "${podName}" > (Join-Path $podDir "describe.txt")
  kubectl --namespace "${namespace}" logs "${podName}" --all-containers=true --ignore-errors --prefix=true > (Join-Path $podDir "logs.txt")
  $restartCount=$(kubectl --namespace "$namespace" get pod "${podName}" -o jsonpath="{.status.containerStatuses[0].restartCount}")
  if ($restartCount -gt 0)
  {
    Write-Output "Pod ${podName} has restarted - collecting previous logs"
    kubectl --namespace "${namespace}" logs "${podName}" --all-containers=true --ignore-errors --prefix=true --previous > (Join-Path $podDir "previous-logs.txt")
  }
  if ($podName -match '.*-worker-.*')
  {
    Write-Output "Collecting status for pod ${podName}"
    $job=Start-Job -ScriptBlock {kubectl port-forward --namespace "${namespace}" "${podName}" 7000:7000}
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri http://localhost:7000/status -OutFile (Join-Path $podDir "status.json")
    Stop-Job $job
  }
}

$date=Get-Date -UFormat "%Y-%m-%dT%H_%M_%S"
$bundle="${namespace}-support-bundle-${date}.tgz"
tar -C "${workDir}" -czvf "${bundle}" "${namespace}"
Write-Output "Created support bundle ${bundle}"

Remove-Item -Recurse -Force -Path "$workDir"

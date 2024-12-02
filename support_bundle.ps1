#!/usr/local/bin/pwsh

param ($namespace=$(kubectl config view --minify -o jsonpath='{..namespace}'))

if ([string]::IsNullOrEmpty($namespace))
{
    Write-Output "No namespace has been provided.`nPlease set one by either doing:`n  .\support_bundle.ps1 <namespace>`nor`n  kubectl config set-context --current --namespace=<namespace>"
    Exit 1
}

$parent = [System.IO.Path]::GetTempPath()
[string] $name = [System.Guid]::NewGuid()
$workDir = Join-Path $parent $name

$null = New-Item -Path "${parent}" -Name "${name}" -ItemType "directory"
$namespaceDir = Join-Path $workDir $namespace
$null = New-Item -Path "${workDir}" -Name "${namespace}" -ItemType "directory"

Write-Output "Collecting events for namespace ${namespace}"
kubectl --namespace "${namespace}" get events --sort-by=lastTimestamp > (Join-Path $namespaceDir "kubernetes_events.txt")

Write-Output "Collecting Helm releases"
$helmDir = Join-Path $namespaceDir "helm"
$null = New-Item -Path "${namespaceDir}" -Name "helm" -ItemType "directory"
helm ls --namespace "${namespace}" > (Join-Path $helmDir "ls.txt")

foreach ($release in $(helm ls -q --namespace "${namespace}").Split([System.Environment]::NewLine))
{
  Write-Output "Collecting values and manifest for Helm release ${release}"
  $releaseDir = Join-Path $helmDir $release
  $null = New-Item -Path "${helmDir}" -Name "${release}" -ItemType "directory"
  helm get values "${release}" --namespace "${namespace}" > (Join-Path $releaseDir "values.txt")
  helm get manifest "${release}" --namespace "${namespace}" > (Join-Path $releaseDir "manifest.txt")
}

$job = Start-Job -ScriptBlock {kubectl proxy}
foreach ($podName in $(kubectl --namespace "$namespace" get pods -o jsonpath="{.items[*].metadata.name}").Split(" "))
{
  Write-Output "Collecting configuration and logs for pod ${podName}"
  $podDir = Join-Path $namespaceDir $podName
  $null = New-Item -Path "${namespaceDir}" -Name "${podName}" -ItemType "directory"
  kubectl --namespace "${namespace}" describe pod "${podName}" > (Join-Path $podDir "describe.txt")
  kubectl --namespace "${namespace}" logs "${podName}" --all-containers=true --ignore-errors --prefix=true > (Join-Path $podDir "logs.txt")
  $restartCount=$(kubectl --namespace "$namespace" get pod "${podName}" -o jsonpath="{.status.containerStatuses[0].restartCount}")
  if ($restartCount -gt 0)
  {
    Write-Output "Pod ${podName} has restarted - collecting previous logs"
    kubectl --namespace "${namespace}" logs "${podName}" --ignore-errors --prefix=true --previous > (Join-Path $podDir "previous-logs.txt")
  }
  if ($podName -match '.*-worker-.*')
  {
    Write-Output "Collecting status for pod ${podName}"
    Invoke-RestMethod -Uri "http://localhost:8001/api/v1/namespaces/${namespace}/pods/${podName}:7000/proxy/status" -OutFile (Join-Path $podDir "status.json")
  }
}
Stop-Job $job

foreach ($restSvcName in (kubectl get svc --namespace $namespace -l app.kubernetes.io/component=rest-worker -o jsonpath="{.items[*].metadata.name}").Split(" ")) {
  $instance = (kubectl get --namespace $namespace svc $restSvcName -o go-template='{{ index .metadata.labels "app.kubernetes.io/instance" }}')

  if (kubectl get secret --namespace $namespace "$instance-rest-api-admin") {
    $configDir = Join-Path $namespaceDir "config" $instance
    $null = New-Item -ItemType Directory -Force -Path $configDir

    Write-Output "Collecting Corda configuration via service $restSvcName"
    $username = (kubectl get secret --namespace $namespace "$instance-rest-api-admin" -o go-template='{{ .data.username | base64decode }}')
    $password = (kubectl get secret --namespace $namespace "$instance-rest-api-admin" -o go-template='{{ .data.password | base64decode }}')

    $job = Start-Job -ScriptBlock {kubectl port-forward --namespace $args[0] $args[1] 9443:443} -ArgumentList $namespace, "svc/${restSvcName}"
    $remainingAttempts = 10
    while ($remainingAttempts -gt 0) {
      if (Test-Connection -ComputerName 'localhost' -TcpPort '9443' -Quiet) {
        break
      }
      Start-Sleep -Seconds 1
      $remainingAttempts--
    }

    if ($remainingAttempts -gt 0) {
      $sections = "crypto", "externalMessaging", "flow", "ledger.utxo", "membership", "messaging", "p2p.gateway", "p2p.linkManager", "rbac", "reconciliation", "rest", "sandbox", "secrets", "security", "stateManager", "vnode.datasource"
      foreach ($section in $sections) {
        Invoke-RestMethod -Uri "https://localhost:9443/api/v5_3/config/corda.$section" -Method Get -SkipCertificateCheck -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${username}:${password}")) } -OutFile (Join-Path $configDir "corda.$section.json")
      }
    }

    Stop-Job $job
  }
  else {
    Write-Output "Unable to collect Corda configuration via service $restSvcName as REST API credentials unavailable"
  }
}

$date=Get-Date -UFormat "%Y-%m-%dT%H_%M_%S"
$bundle="${namespace}-support-bundle-${date}.zip"
Compress-Archive -Path "${workDir}\${namespace}" -DestinationPath "${bundle}"
Write-Output "Created support bundle ${bundle}"

$null = Remove-Item -Recurse -Force -Path "$workDir"

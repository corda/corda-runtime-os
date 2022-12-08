#!/usr/local/bin/pwsh

param ($namespace=$(kubectl config view --minify -o jsonpath='{..namespace}'))

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
    kubectl --namespace "${namespace}" logs "${podName}" --all-containers=true --ignore-errors --prefix=true --previous > (Join-Path $podDir "previous-logs.txt")
  }
  if ($podName -match '.*-worker-.*')
  {
    Write-Output "Collecting status for pod ${podName}"
    $job=Start-Job -ScriptBlock {kubectl port-forward --namespace $args[0] $args[1] 7000:7000} -ArgumentList $namespace,$podName
    $ProgressPreference = 'SilentlyContinue'
    # Retry needed as, at least on macOS, the forwarded port is not immediately available
    $count = 0
    do
    {
      $count++
      try
      {
        Invoke-RestMethod -Uri http://localhost:7000/status -OutFile (Join-Path $podDir "status.json")
        break
      }
      catch
      {
        if ($count -eq 10)
        {
          Write-Error $_.Exception.InnerException.Message -ErrorAction Continue
        }
        else
        {
          Start-Sleep -Seconds 10
        }
      }
    } while ($count -lt 5)
    Stop-Job $job
  }
}

$date=Get-Date -UFormat "%Y-%m-%dT%H_%M_%S"
$bundle="${namespace}-support-bundle-${date}.zip"
Compress-Archive -Path "${workDir}\${namespace}" -DestinationPath "${bundle}"
Write-Output "Created support bundle ${bundle}"

$null = Remove-Item -Recurse -Force -Path "$workDir"

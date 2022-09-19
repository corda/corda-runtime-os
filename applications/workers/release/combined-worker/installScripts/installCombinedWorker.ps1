param ($addToPath)

$combinedWorkerDir = "$ENV:UserProfile\.corda"

Write-Output "Creating combined-worker dir at $combinedWorkerDir"
New-Item -Path "$combinedWorkerDir" -ItemType "directory" -Force

Write-Output "copying files"
Copy-Item -Path ".\*" -Destination $combinedWorkerDir -Recurse

Write-Output "Creating combined-worker Script"
$combinedWorkerCommand = "$ENV:JAVA_HOME\bin\java -jar corda-combined-worker.jar %*"
New-Item "$combinedWorkerDir\corda-combined-worker.cmd" -ItemType File -Value $combinedWorkerCommand

if($addToPath) {
    Write-Output "Adding combined-worker to path"

    Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name path
    $old = (Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name path).path
    $new  =  "$old;$combinedWorkerDir"
    Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name path -Value $new
}
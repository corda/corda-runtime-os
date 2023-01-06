param ($addToPath)

$cliHomeDir = "$ENV:UserProfile\.corda\cli"

Write-Output "Creating corda-cli dir at $cliHomeDir"
New-Item -Path "$cliHomeDir" -ItemType "directory" -Force

Write-Output "copying files and plugins"
Copy-Item -Path ".\*" -Destination $cliHomeDir -Recurse

Write-Output "Creating corda-cli Script"
$cliCommand = "`"$ENV:JAVA_HOME\bin\java`" -Dpf4j.pluginsDir=`"$cliHomeDir\plugins`" -jar `"$cliHomeDir\corda-cli.jar`" %*"
New-Item "$cliHomeDir\corda-cli.cmd" -ItemType File -Value $cliCommand

if($addToPath) {
    Write-Output "Adding corda-cli to path"

    Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name path
    $old = (Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name path).path
    $new = "$old;$cliHomeDir"
    Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name path -Value $new
}
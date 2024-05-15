param ($addToPath)

$cliHomeDir = "$ENV:UserProfile\.corda\cli"

Write-Output "Removing previous cli version if exists"
if(Test-Path -Path "$cliHomeDir") {
    Write-Output "Deleting: $cliHomeDir"
    Remove-Item "$cliHomeDir" -Recurse
}

Write-Output "Creating corda-cli dir at $cliHomeDir"
New-Item -Path "$cliHomeDir" -ItemType "directory" -Force

Write-Output "Copying files and plugins"
Copy-Item -Path ".\*" -Destination $cliHomeDir -Recurse

Write-Output "Creating corda-cli Script"
$cliCommand = "`"$ENV:JAVA_HOME\bin\java`" -Dpf4j.pluginsDir=`"$cliHomeDir\plugins`" -jar `"$cliHomeDir\corda-cli.jar`" %*"
New-Item "$cliHomeDir\corda-cli.cmd" -ItemType File -Value $cliCommand

if($addToPath) {

    # Set permanently for the User
    $old = [Environment]::GetEnvironmentVariable('PATH', 'User')
    if (-Not $old.ToLower().Contains($cliHomeDir.ToLower())) {
        Write-Output "Adding '$cliHomeDir' to path of User profile"
        $new = "$old;$cliHomeDir"
        [Environment]::SetEnvironmentVariable('PATH', $new, 'User')
    }

    # Set in the current terminal
    $old = [Environment]::GetEnvironmentVariable('PATH')
    if (-Not $old.ToLower().Contains($cliHomeDir.ToLower())) {
        Write-Output "Adding '$cliHomeDir' to path of current session"
        $new = "$old;$cliHomeDir"
        [Environment]::SetEnvironmentVariable('PATH', $new)
    }

}
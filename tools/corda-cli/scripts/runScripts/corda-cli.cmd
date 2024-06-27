@echo off

setlocal

:: get path to script
for %%I in ("%~dp0") do set "jarPath=%%~fI"

:: Resolve any "." and ".." in jarPath to make it shorter.
for %%i in ("%jarPath%") do set "jarPath=%%~fi"

java -jar "%jarPath%\corda-cli.jar" %*

endlocal
@echo off

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

SET binDir="%APP_HOME%\app\build\libs"
SET pluginsDir="%APP_HOME%\build\plugins"

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -Dpf4j.pluginsDir=%pluginsDir% -jar %binDir%\corda-cli-0.0.1-beta.jar %*

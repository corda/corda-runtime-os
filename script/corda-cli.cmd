@echo off
cd ..\
SET rootDir=%cd%
SET pluginsDir="%rootDir%\build\plugins"
cd script

java -Dpf4j.pluginsDir=%pluginsDir% -jar ..\app\build\libs\corda-cli.jar %*
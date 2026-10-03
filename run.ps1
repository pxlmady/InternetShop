# Збирає проєкт Maven і запускає застосунок: http://localhost:8080. Ctrl+C зупиняє сервер.
# Потрібні JDK 21+ і Maven (mvn) у PATH.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& mvn -q package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
[Console]::OutputEncoding = [Text.Encoding]::UTF8
& java '-Dstdout.encoding=UTF-8' -jar internetshop-webapi/target/internetshop.jar 8080 data
exit $LASTEXITCODE

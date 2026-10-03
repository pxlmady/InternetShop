# Повна збірка Maven із запуском консольних перевірок (Domain, сховище, межі шарів, HTTP). Сервер не потрібен.
# Потрібні JDK 21+ і Maven (mvn) у PATH.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
[Console]::OutputEncoding = [Text.Encoding]::UTF8
& mvn verify
exit $LASTEXITCODE

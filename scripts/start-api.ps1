# Démarre l'API Spring Boot (profil dev par défaut) ou le worker d'import.
# Usage : .\scripts\start-api.ps1            -> API sur http://localhost:8080
#         .\scripts\start-api.ps1 -Worker    -> worker d'import CSV
param([switch]$Worker)
$ErrorActionPreference = "Stop"
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) "api")
try {
  $jar = "target\proclama-api.jar"
  if (-not (Test-Path $jar)) {
    Write-Host "[api] jar absent : compilation..." -ForegroundColor Cyan
    & mvn -B -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package a echoue." }
  }
  if ($Worker) {
    & java -jar $jar --app.mode=worker --spring.main.web-application-type=none
  } else {
    & java -jar $jar
  }
} finally { Pop-Location }

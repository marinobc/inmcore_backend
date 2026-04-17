$services = @(
    "access-control-service",
    "api-gateway",
    "identity-service",
    "notification-service",
    "operation-service",
    "property-service",
    "service-registry",
    "user-service",
    "visit-calendar-service"
)

$root = $PSScriptRoot

foreach ($service in $services) {
    $path = Join-Path $root $service
    Write-Host "`n==> Processing: $service" -ForegroundColor Cyan
    Push-Location $path

    # Apply formatting
    Write-Host "  Applying Spotless format..." -ForegroundColor Yellow
    mvn spotless:apply

    # Clean any cached files
    Write-Host "  Cleaning..." -ForegroundColor Yellow
    mvn clean

    # Compile to ensure everything is correct
    Write-Host "  Compiling..." -ForegroundColor Yellow
    mvn compile -DskipTests

    Pop-Location
}

Write-Host "`nDone! All services formatted and compiled." -ForegroundColor Green

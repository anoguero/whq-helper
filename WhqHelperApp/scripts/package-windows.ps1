param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "exe"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$PomPath = Join-Path $ProjectRoot "pom.xml"
[xml]$Pom = Get-Content $PomPath

$ArtifactId = $Pom.project.artifactId
$Version = $Pom.project.version
$MainJar = "$ArtifactId-$Version.jar"
$WindowsInputDir = Join-Path $ProjectRoot "target/windows-input"
$OutputDir = Join-Path $ProjectRoot "target/windows-package"
$WindowsSwtJar = Join-Path $ProjectRoot "lib/org.eclipse.swt.win32.win32.x86_64-3.127.0.jar"

if (-not (Test-Path $WindowsSwtJar)) {
    throw "Falta el JAR de SWT para Windows: $WindowsSwtJar"
}

Push-Location $ProjectRoot
try {
    mvn -q -Pwindows-dist -DskipTests clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Ha fallado la construcción Maven del bundle Windows."
    }

    $MainJarPath = Join-Path $WindowsInputDir $MainJar
    if (-not (Test-Path $MainJarPath)) {
        throw "No se ha generado el bundle de entrada para Windows: $MainJarPath"
    }

    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

    $JPackageArgs = @(
        "--type", $Type,
        "--input", $WindowsInputDir,
        "--dest", $OutputDir,
        "--name", "WHQ Helper",
        "--main-jar", $MainJar,
        "--main-class", "com.whq.app.WhqCardRendererApp",
        "--app-version", $Version,
        "--vendor", "WHQ Helper",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--win-shortcut"
    )

    & jpackage @JPackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Ha fallado jpackage al generar el instalador Windows."
    }
} finally {
    Pop-Location
}

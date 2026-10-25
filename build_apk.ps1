$ErrorActionPreference = 'Stop'

$projectRoot = "C:\Users\Tilak\.gemini\antigravity\scratch\PowerTrack"
$standaloneDir = Join-Path $projectRoot "android-standalone"
$toolchainDir = "C:\Users\Tilak\.gemini\antigravity\scratch\android_toolchain"
$jdkDir = Join-Path $toolchainDir "jdk-17"
$btDir = Join-Path $toolchainDir "build-tools"
$platJar = Join-Path $toolchainDir "android-13\android.jar"

$buildDir = Join-Path $standaloneDir "build_output"
$outputApk = Join-Path $projectRoot "PowerTrack-v1.0.apk"

$env:JAVA_HOME = $jdkDir
$env:PATH = "$jdkDir\bin;$btDir;$env:PATH"

$java = Join-Path $jdkDir "bin\java.exe"
$javac = Join-Path $jdkDir "bin\javac.exe"
$jar = Join-Path $jdkDir "bin\jar.exe"
$keytool = Join-Path $jdkDir "bin\keytool.exe"
$aapt2 = Join-Path $btDir "aapt2.exe"
$zipalign = Join-Path $btDir "zipalign.exe"
$d8Jar = Join-Path $btDir "lib\d8.jar"
$apksignerJar = Join-Path $btDir "lib\apksigner.jar"

Write-Output "=== Starting PowerTrack APK Build Pipeline ==="

# Clean build directory
if (Test-Path $buildDir) {
    Remove-Item $buildDir -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $buildDir "gen") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $buildDir "classes") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $buildDir "dex") | Out-Null

$resDir = Join-Path $standaloneDir "src\main\res"
$manifestPath = Join-Path $standaloneDir "src\main\AndroidManifest.xml"
$resZip = Join-Path $buildDir "resources.zip"
$baseApk = Join-Path $buildDir "app-base.apk"
$alignedApk = Join-Path $buildDir "app-aligned.apk"
$keystore = Join-Path $buildDir "powertrack-debug.keystore"

# Step 1: AAPT2 Compile Resources
Write-Output "Step 1: Compiling Android Resources with AAPT2..."
& $aapt2 compile --dir $resDir -o $resZip
if ($LASTEXITCODE -ne 0) { throw "AAPT2 compile failed" }

# Step 2: AAPT2 Link Resources & Generate R.java
Write-Output "Step 2: Linking Resources & Packaging Base APK..."
& $aapt2 link -I $platJar $resZip --manifest $manifestPath --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name "1.0.0" --java (Join-Path $buildDir "gen") -o $baseApk --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "AAPT2 link failed" }

# Step 3: Compile Java Sources
Write-Output "Step 3: Compiling Java Sources with javac (JDK 17)..."
$javaFiles = Get-ChildItem -Path (Join-Path $standaloneDir "src\main\java"), (Join-Path $buildDir "gen") -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
Write-Output "Found $($javaFiles.Count) Java files to compile."

$argFile = Join-Path $buildDir "sources.txt"
[System.IO.File]::WriteAllLines($argFile, [string[]]$javaFiles, [System.Text.Encoding]::ASCII)
& $javac -cp $platJar -d (Join-Path $buildDir "classes") -source 1.8 -target 1.8 "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed" }

# Step 4: D8 Dexing
Write-Output "Step 4: Compiling bytecode to classes.dex using D8..."
$classFiles = Get-ChildItem -Path (Join-Path $buildDir "classes") -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName
& $java -cp $d8Jar com.android.tools.r8.D8 --release --min-api 26 --lib $platJar --output (Join-Path $buildDir "dex") $classFiles
if ($LASTEXITCODE -ne 0) { throw "D8 dex compilation failed" }

# Step 5: Add DEX to APK
Write-Output "Step 5: Injecting classes.dex into APK package..."
Push-Location (Join-Path $buildDir "dex")
& $jar uf $baseApk classes.dex
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "JAR packaging failed" }

# Step 6: Zipalign 4-byte boundaries
Write-Output "Step 6: Optimizing APK alignment (4-byte boundary) with zipalign..."
& $zipalign -f -p 4 $baseApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# Step 7: Generate Keystore & Sign APK
Write-Output "Step 7: Signing APK with apksigner (v1, v2, v3 schemes)..."
& $keytool -genkeypair -validity 10000 -dname "CN=PowerTrack,O=PowerTrack,C=US" -keystore $keystore -storepass android -keypass android -alias powertrackdebug -keyalg RSA -keysize 2048
if ($LASTEXITCODE -ne 0) { throw "Keytool failed" }

& $java -jar $apksignerJar sign --ks $keystore --ks-pass pass:android --ks-key-alias powertrackdebug --key-pass pass:android --out $outputApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

# Step 8: Verify Signature
Write-Output "Step 8: Verifying APK Signature integrity..."
& $java -jar $apksignerJar verify --verbose $outputApk

$apkItem = Get-Item $outputApk
Write-Output "=================================================="
Write-Output "SUCCESS! PowerTrack Installable APK successfully created:"
Write-Output "File: $($apkItem.FullName)"
Write-Output "Size: $([math]::Round($apkItem.Length / 1KB, 2)) KB"
Write-Output "=================================================="

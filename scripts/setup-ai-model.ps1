param(
    [string]$MirrorBase = "https://hf-mirror.com/onnx-community/bge-small-zh-v1.5-ONNX/resolve/main"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$targetDirectory = Join-Path $projectRoot ".runtime\models\bge-small-zh-v1.5"
New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null

$files = @(
    @{ Path = "tokenizer.json"; Size = 362603 },
    @{ Path = "onnx/model_quantized.onnx"; Size = 168002 },
    @{ Path = "onnx/model_quantized.onnx_data"; Size = 23774208 }
)

foreach ($file in $files) {
    $name = Split-Path -Leaf $file.Path
    $destination = Join-Path $targetDirectory $name
    if ((Test-Path -LiteralPath $destination) -and
        (Get-Item -LiteralPath $destination).Length -eq $file.Size) {
        Write-Host "Already present: $name"
        continue
    }

    $temporary = "$destination.part"
    Invoke-WebRequest -Uri "$MirrorBase/$($file.Path)" -OutFile $temporary -UseBasicParsing
    if ((Get-Item -LiteralPath $temporary).Length -ne $file.Size) {
        Remove-Item -LiteralPath $temporary -Force
        throw "Unexpected file size for $name"
    }
    Move-Item -LiteralPath $temporary -Destination $destination -Force
    Write-Host "Downloaded: $name"
}

# Spring AI loads the ONNX graph from bytes, so ONNX Runtime resolves external
# tensor data against the process working directory rather than the model file.
$externalData = Join-Path $targetDirectory "model_quantized.onnx_data"
foreach ($workingDirectory in $projectRoot, (Join-Path $projectRoot "backend")) {
    Copy-Item -LiteralPath $externalData `
        -Destination (Join-Path $workingDirectory "model_quantized.onnx_data") -Force
}

Write-Host "Embedding model is ready in $targetDirectory"

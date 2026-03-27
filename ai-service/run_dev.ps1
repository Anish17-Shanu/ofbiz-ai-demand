# AI Demand Service quick run
# Creates a predictable local start path from the project venv.

$python = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    Write-Error "Virtual environment not found at $python. Create it first with: python -m venv .venv"
    exit 1
}

& $python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload

@echo off
setlocal

cd /d "%~dp0"

where npm >nul 2>&1
if errorlevel 1 (
    echo [ERROR] npm was not found. Install Node.js and reopen this window.
    pause
    exit /b 1
)

if not exist "node_modules\" (
    echo Installing frontend dependencies...
    call npm ci
    if errorlevel 1 (
        echo [ERROR] Failed to install frontend dependencies.
        pause
        exit /b 1
    )
)

echo Starting frontend at http://localhost:5173
call npm run dev

endlocal

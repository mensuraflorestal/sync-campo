@echo off
title RS Florestal - Sincronizador de Campo
cd /d "%~dp0"
chcp 65001 >nul 2>&1
set PYTHONIOENCODING=utf-8
set PYTHONUTF8=1
echo =========================================================
echo    Iniciando RS Florestal Sync com Cloudflare Tunnel...
echo =========================================================
if not exist ".venv\Scripts\python.exe" (
    echo [Instalacao Inicial] Criando ambiente virtual .venv no servidor...
    python -m venv .venv
    .\.venv\Scripts\python.exe -m pip install --upgrade pip
    .\.venv\Scripts\python.exe -m pip install -r requirements.txt
)
.\.venv\Scripts\python.exe run.py
pause

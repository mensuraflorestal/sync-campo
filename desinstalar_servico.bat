@echo off
title Desinstalacao do Servico - RS Florestal SST
cd /d "%~dp0"
chcp 65001 >nul

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ==============================================================
    echo  ERRO: Execute este arquivo como ADMINISTRADOR!
    echo ==============================================================
    pause
    exit /b 1
)

set SERVICE_NAME=RSFlorestalSST
set NSSM_EXE=%~dp0nssm.exe

echo Parando servico %SERVICE_NAME%...
"%NSSM_EXE%" stop %SERVICE_NAME%

echo Removendo servico %SERVICE_NAME%...
"%NSSM_EXE%" remove %SERVICE_NAME% confirm

echo.
echo ==============================================================
echo  Servico removido com sucesso do Windows!
echo ==============================================================
echo.
pause


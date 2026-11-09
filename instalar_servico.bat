@echo off
setlocal EnableDelayedExpansion
title Instalador do Servico - RS Florestal SST
cd /d "%~dp0"

:: 1. Auto-elevacao para Administrador
NET FILE 1>NUL 2>NUL
if '%errorlevel%' NEQ '0' (
    echo [Solicitando privilegios de Administrador...]
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /B
)

chcp 65001 >nul 2>&1
cls

echo =======================================================================
echo    INSTALADOR DE SERVICO WINDOWS - RS FLORESTAL SST
echo =======================================================================
echo.
echo Diretorio atual: %~dp0
echo.

:: 2. Localiza o executavel do Python
set "PYTHON_CMD="

if exist "%~dp0.venv\Scripts\python.exe" (
    set "PYTHON_CMD=%~dp0.venv\Scripts\python.exe"
    echo [OK] Ambiente virtual .venv existente detectado.
    goto :python_ok
)

:: Testa se python funciona no terminal
where python >nul 2>&1
if %errorlevel% equ 0 (
    set "PYTHON_CMD=python"
    goto :create_venv
)

:: Testa se py funciona no terminal
where py >nul 2>&1
if %errorlevel% equ 0 (
    set "PYTHON_CMD=py"
    goto :create_venv
)

:: Procura nos caminhos padroes de instalacao no Windows Server
for %%P in (
    "C:\Python311\python.exe"
    "C:\Python310\python.exe"
    "C:\Python39\python.exe"
    "C:\Python38\python.exe"
    "C:\Python38-32\python.exe"
    "C:\Program Files\Python311\python.exe"
    "C:\Program Files\Python310\python.exe"
    "C:\Program Files\Python39\python.exe"
    "C:\Program Files\Python38\python.exe"
    "%LocalAppData%\Programs\Python\Python311\python.exe"
    "%LocalAppData%\Programs\Python\Python310\python.exe"
    "%LocalAppData%\Programs\Python\Python39\python.exe"
    "%LocalAppData%\Programs\Python\Python38\python.exe"
) do (
    if exist "%%~P" (
        set "PYTHON_CMD=%%~P"
        echo [OK] Python localizado em: %%~P
        goto :create_venv
    )
)

echo.
echo =======================================================================
echo  ERRO: O Python nao foi encontrado no Windows Server!
echo =======================================================================
echo  Certifique-se de que o Python 3.8+ esta instalado no servidor.
echo  Caso ja tenha instalado, adicione-o ao PATH do Windows.
echo =======================================================================
echo.
pause
exit /B 1

:create_venv
echo [1/4] Criando ambiente virtual Python .venv...
"%PYTHON_CMD%" -m venv "%~dp0.venv"
if not exist "%~dp0.venv\Scripts\python.exe" (
    echo.
    echo [ERRO] Falha ao criar a pasta .venv
    pause
    exit /B 1
)

echo [1/4] Instalando bibliotecas necessarias de requirements.txt...
"%~dp0.venv\Scripts\python.exe" -m pip install --upgrade pip
"%~dp0.venv\Scripts\python.exe" -m pip install -r "%~dp0requirements.txt"

:python_ok
set "APP_DIR=%~dp0"
if "%APP_DIR:~-1%"=="\" set "APP_DIR=%APP_DIR:~0,-1%"

if not exist "%APP_DIR%\storage" mkdir "%APP_DIR%\storage"

set "SERVICE_NAME=RSFlorestalSST"
set "NSSM_EXE=%APP_DIR%\nssm.exe"
set "TARGET_PYTHON=%APP_DIR%\.venv\Scripts\python.exe"

if not exist "%NSSM_EXE%" (
    echo.
    echo [ERRO] O arquivo nssm.exe nao foi encontrado na pasta: %APP_DIR%
    pause
    exit /B 1
)

echo [2/4] Parando e limpando registro anterior do servico...
"%NSSM_EXE%" stop %SERVICE_NAME% >nul 2>&1
"%NSSM_EXE%" remove %SERVICE_NAME% confirm >nul 2>&1

echo [3/4] Registrando servico no Windows Server...
"%NSSM_EXE%" install %SERVICE_NAME% "%TARGET_PYTHON%" "-u run.py"
"%NSSM_EXE%" set %SERVICE_NAME% AppDirectory "%APP_DIR%"
"%NSSM_EXE%" set %SERVICE_NAME% DisplayName "RS Florestal - Sincronizador SST"
"%NSSM_EXE%" set %SERVICE_NAME% Description "Sincronizador offline de documentos SST com Cloudflare Tunnel sst.florestalrs.com.br"
"%NSSM_EXE%" set %SERVICE_NAME% Start SERVICE_AUTO_START
"%NSSM_EXE%" set %SERVICE_NAME% AppEnvironmentExtra "PYTHONIOENCODING=utf-8" "PYTHONUTF8=1" "PYTHONUNBUFFERED=1"

:: Logs e recuperacao automatica
"%NSSM_EXE%" set %SERVICE_NAME% AppStdout "%APP_DIR%\storage\servico_stdout.log"
"%NSSM_EXE%" set %SERVICE_NAME% AppStderr "%APP_DIR%\storage\servico_stderr.log"
"%NSSM_EXE%" set %SERVICE_NAME% AppRotateFiles 1
"%NSSM_EXE%" set %SERVICE_NAME% AppRotateBytes 10485760
"%NSSM_EXE%" set %SERVICE_NAME% AppRestartDelay 5000

echo [4/4] Iniciando servico...
"%NSSM_EXE%" start %SERVICE_NAME%

echo.
echo =======================================================================
echo  SUCESSO: O RS Florestal SST agora esta ATIVO como Servico do Windows!
echo =======================================================================
echo  - Inicializacao: AUTOMATICA - Inicia sozinho com o servidor
echo  - Sem janelas abertas: Roda 100%% em segundo plano
echo  - Gerenciamento: Digite services.msc no Executar para ver o servico
echo  - Logs gravados em: storage\servico_stdout.log
echo =======================================================================
echo.
pause

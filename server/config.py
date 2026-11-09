import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

# Carrega variáveis do arquivo .env
try:
    from dotenv import load_dotenv
    load_dotenv(BASE_DIR / ".env")
except ImportError:
    env_file = BASE_DIR / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip())

# Diretório para cache de arquivos baixados do Google Drive
CACHE_DIR = BASE_DIR / "storage" / "cache"
CACHE_DIR.mkdir(parents=True, exist_ok=True)

# Diretório de arquivos de demonstração (para testar antes de plugar o Drive)
MOCK_DIR = BASE_DIR / "storage" / "demo_files"
MOCK_DIR.mkdir(parents=True, exist_ok=True)

# Caminho para arquivo de credenciais da conta de serviço Google
CREDENTIALS_FILE = BASE_DIR / "service_account.json"

# ID da pasta central compartilhada no Google Drive
# Exemplo: https://drive.google.com/drive/folders/1a2b3c4d5e... -> ID é '1a2b3c4d5e...'
GOOGLE_DRIVE_FOLDER_ID = os.getenv("GOOGLE_DRIVE_FOLDER_ID", "")

# Configuração de rede e segurança
SERVER_HOST = os.getenv("HOST", "0.0.0.0")
SERVER_PORT = int(os.getenv("PORT", "8000"))
SST_AUTH_TOKEN = os.getenv("SST_AUTH_TOKEN", "rsf_sec_9d81a4f028bc1723e4")


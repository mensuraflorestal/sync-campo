import os
import re
import sys
import socket
import uvicorn
import threading
import subprocess
from pathlib import Path

os.environ["PYTHONIOENCODING"] = "utf-8"
os.environ["PYTHONUTF8"] = "1"
os.environ["PYTHONUNBUFFERED"] = "1"

try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
except Exception:
    pass

BASE_DIR = Path(__file__).resolve().parent
CLOUDFLARED_EXE = BASE_DIR / "cloudflared.exe"
TUNNEL_UUID = "e7ef0bd8-742a-4ede-90b0-07d1874b02d2"

def get_local_ip():
    """Detecta o endereco IP local na rede Wi-Fi / Ethernet."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.254.254.254', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

def print_banner(public_url: str = None, local_ip: str = "127.0.0.1", port: int = 8000):
    apk_url = f"{public_url}/app.apk" if public_url else f"http://{local_ip}:{port}/app.apk"
    
    print("\n" + "=" * 68)
    print("   DOCUMENTOS SST - OPERACOES DE CAMPO OFFLINE (RS FLORESTAL)")
    print("=" * 68)
    print(" [MODO BLINDADO ATIVO]:")
    print(" - Interface Web desativada (Documentos acessiveis apenas via App).")
    print("-" * 68)
    if public_url:
        print(" SERVIDOR OFICIAL (HTTPS - MUNDIAL):")
        print(f" -> Gateway: {public_url}")
        print(f" -> Download do APK Oficial: {apk_url}")
    else:
        print(f" -> Download APK (Rede Local): {apk_url}")
    print("=" * 68)
    print(" [OK] Iniciando motor FastAPI na porta 8000... Pressione Ctrl + C para encerrar.")
    print("=" * 68 + "\n")

def start_cloudflare_tunnel(port: int, on_url_found):
    """Inicia o processo do Cloudflare Tunnel e captura a URL publica."""
    if not CLOUDFLARED_EXE.exists():
        print("\n[Aviso] cloudflared.exe nao encontrado. Iniciando apenas em modo de rede local.")
        return None

    config_file = BASE_DIR / "config.yml"
    cred_file = BASE_DIR / f"{TUNNEL_UUID}.json"
    is_fixed = config_file.exists() and cred_file.exists()

    if is_fixed:
        cmd = [
            str(CLOUDFLARED_EXE),
            "tunnel",
            "--config", str(config_file),
            "--credentials-file", str(cred_file),
            "--no-autoupdate",
            "run", TUNNEL_UUID
        ]
        on_url_found("https://sst.florestalrs.com.br")
    else:
        cmd = [
            str(CLOUDFLARED_EXE),
            "tunnel",
            "--url", f"http://127.0.0.1:{port}",
            "--no-autoupdate"
        ]

    try:
        proc = subprocess.Popen(
            cmd,
            cwd=str(BASE_DIR),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace"
        )
    except Exception as e:
        print(f"[Erro ao iniciar Cloudflare Tunnel]: {e}")
        return None

    def monitor_output():
        tunnel_url = None
        for line in proc.stderr:
            if "Registered tunnel connection" in line or ("Connection" in line and "registered" in line):
                print("[Cloudflare Tunnel] Conexao estabelecida com sucesso!")
            elif "ERR" in line and "ICMP" not in line and "certPool" not in line:
                print(f"[Cloudflare Log] {line.strip()}")
            if not is_fixed and not tunnel_url:
                match = re.search(r'https://[a-zA-Z0-9-]+\.trycloudflare\.com', line)
                if match:
                    tunnel_url = match.group(0)
                    on_url_found(tunnel_url)

    t = threading.Thread(target=monitor_output, daemon=True)
    t.start()
    return proc

if __name__ == "__main__":
    port = 8000
    local_ip = get_local_ip()
    tunnel_proc = None
    
    url_event = threading.Event()
    discovered_url = {"url": None}

    def on_url_found(url):
        discovered_url["url"] = url
        url_event.set()

    if CLOUDFLARED_EXE.exists():
        print("\n[1/2] Conectando ao Cloudflare Tunnel (https://sst.florestalrs.com.br)...")
        tunnel_proc = start_cloudflare_tunnel(port, on_url_found)
        url_event.wait(timeout=15)

    print_banner(discovered_url["url"], local_ip, port)

    try:
        uvicorn.run("server.main:app", host="0.0.0.0", port=port, log_level="warning")
    finally:
        if tunnel_proc:
            print("\n[Encerrando Cloudflare Tunnel...]")
            tunnel_proc.terminate()
            try:
                tunnel_proc.wait(timeout=3)
            except Exception:
                tunnel_proc.kill()

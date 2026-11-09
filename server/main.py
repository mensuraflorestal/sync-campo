import urllib.parse
from fastapi import FastAPI, HTTPException, Response, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

from .config import BASE_DIR
from .drive_service import drive_service

app = FastAPI(
    title="SyncCampo API",
    description="Backend para sincronizacao offline de documentos de campo do Google Drive",
    version="1.0.0"
)

# Habilita CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class FolderConfigModel(BaseModel):
    folder_id: str


@app.get("/api/status")
def get_status():
    """Retorna o status de conexao com o Drive e resumo de arquivos (em threadpool paralelo)."""
    status = drive_service.get_status()
    all_items = drive_service.list_files()
    files = [f for f in all_items if not f.get("is_folder")]
    folders = [f for f in all_items if f.get("is_folder")]
    total_size = sum(f.get("size", 0) for f in files)
    
    status.update({
        "files_count": len(files),
        "folders_count": len(folders),
        "total_items": len(all_items),
        "total_size": total_size,
        "total_size_formatted": drive_service._format_size(total_size)
    })
    return status


@app.get("/api/files")
def list_files():
    """Lista todos os arquivos da pasta para sincronizacao com o celular (em threadpool paralelo)."""
    files = drive_service.list_files()
    num_files = len([f for f in files if not f.get("is_folder")])
    num_folders = len([f for f in files if f.get("is_folder")])
    print(f"\n[App SST] Celular solicitou lista de documentos: {num_files} arquivos, {num_folders} pastas.")
    return {"files": files}


@app.get("/api/files/{file_id}/download")
def download_file(file_id: str):
    """Transfere o arquivo para multiplos celulares simultaneamente sem bloquear o event loop."""
    result = drive_service.get_file_content(file_id)
    if not result:
        print(f" [Aviso App SST] Arquivo nao encontrado ou erro de download: {file_id}")
        raise HTTPException(status_code=404, detail="Arquivo nao encontrado ou erro ao baixar do Google Drive.")

    content, mime_type, filename = result
    size_str = drive_service._format_size(len(content))
    print(f" [Download Celular] Enviando: {filename} ({size_str})")
    
    # Tratamento para nomes com acentos e caracteres especiais
    encoded_filename = urllib.parse.quote(filename)
    
    headers = {
        "Content-Disposition": f"inline; filename*=UTF-8''{encoded_filename}",
        "Access-Control-Expose-Headers": "Content-Disposition",
        "Cache-Control": "public, max-age=3600"
    }
    
    return Response(
        content=content,
        media_type=mime_type,
        headers=headers
    )


@app.post("/api/settings/folder")
def update_folder_id(data: FolderConfigModel):
    """Atualiza o ID da pasta do Google Drive."""
    drive_service.set_folder_id(data.folder_id)
    return {
        "success": True,
        "status": drive_service.get_status()
    }


@app.get("/sw.js")
async def get_service_worker():
    """Entrega um Service Worker de autodestruicao para limpar o cache de navegadores antigos."""
    sw_code = (
        "self.addEventListener('install', (e) => { self.skipWaiting(); });\n"
        "self.addEventListener('activate', (e) => {\n"
        "  e.waitUntil(\n"
        "    caches.keys().then((keys) => Promise.all(keys.map((k) => caches.delete(k))))\n"
        "      .then(() => self.registration.unregister())\n"
        "      .then(() => self.clients.matchAll())\n"
        "      .then((clients) => { clients.forEach(c => c.navigate(c.url)); })\n"
        "  );\n"
        "});\n"
    )
    return Response(
        content=sw_code,
        media_type="application/javascript",
        headers={
            "Service-Worker-Allowed": "/",
            "Cache-Control": "no-cache, no-store, must-revalidate"
        }
    )


@app.get("/app.apk")
@app.get("/sync_campo.apk")
async def get_apk():
    """Download direto do instalador oficial do APK Android."""
    for name in ("app.apk", "sync_campo.apk"):
        apk_path = BASE_DIR / name
        if apk_path.exists():
            return FileResponse(
                apk_path,
                media_type="application/vnd.android.package-archive",
                filename="app.apk",
                headers={
                    "Cache-Control": "no-cache, no-store, must-revalidate, max-age=0",
                    "Pragma": "no-cache",
                    "Expires": "0"
                }
            )
    raise HTTPException(status_code=404, detail="APK nao encontrado")

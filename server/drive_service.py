from __future__ import annotations
import os
import io
import json
import time
import threading
import mimetypes
import hashlib
from pathlib import Path
from typing import List, Dict, Any, Optional
from datetime import datetime

from .config import (
    CREDENTIALS_FILE,
    GOOGLE_DRIVE_FOLDER_ID,
    CACHE_DIR,
    MOCK_DIR
)
from .mock_data import setup_demo_files

# Tentativa de import da biblioteca do Google
try:
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaIoBaseDownload
    GOOGLE_LIBS_AVAILABLE = True
except ImportError:
    GOOGLE_LIBS_AVAILABLE = False

UNSUPPORTED_DRIVE_TYPES = {
    "application/vnd.google-apps.folder",
    "application/vnd.google-apps.shortcut",
    "application/vnd.google-apps.form",
    "application/vnd.google-apps.map",
    "application/vnd.google-apps.site",
}


class DriveService:
    def __init__(self):
        self.folder_id = GOOGLE_DRIVE_FOLDER_ID
        self.credentials_path = CREDENTIALS_FILE
        self._service = None
        self._creds = None
        self._lock = threading.RLock()
        self._items_cache: List[Dict[str, Any]] = []
        self._items_cache_time: float = 0
        self._metadata_map: Dict[str, Dict[str, Any]] = {}
        self._precaching: bool = False
        self._refreshing_catalog: bool = False
        self._watcher_started: bool = False
        self._failed_ids: set = set()
        self._catalog_file = CACHE_DIR / "catalog_cache.json"
        self._load_disk_catalog()
        self._init_service()

    def _load_disk_catalog(self):
        """Carrega o catálogo salvo no disco do PC para resposta instantânea (0ms) logo ao ligar."""
        try:
            if self._catalog_file.exists():
                data = json.loads(self._catalog_file.read_text(encoding="utf-8"))
                if isinstance(data, list) and data:
                    self._items_cache = data
                    for item in data:
                        if not item.get("is_folder") and item.get("id"):
                            self._metadata_map[item["id"]] = item
        except Exception as e:
            print(f"[Cache] Aviso ao ler catálogo local: {e}")

    def _save_disk_catalog(self, items: List[Dict[str, Any]]):
        """Persiste o catálogo no SSD para recuperação imediata."""
        try:
            tmp = CACHE_DIR / "catalog_cache.json.tmp"
            tmp.write_text(json.dumps(items, ensure_ascii=False), encoding="utf-8")
            tmp.replace(self._catalog_file)
        except Exception as e:
            print(f"[Cache] Aviso ao salvar catálogo local: {e}")

    def _init_service(self, trigger_precache: bool = True):
        """Inicializa o serviço do Google Drive caso as credenciais existam."""
        if not GOOGLE_LIBS_AVAILABLE:
            return

        if self.credentials_path.exists() and self.folder_id:
            try:
                scopes = ['https://www.googleapis.com/auth/drive.readonly']
                self._creds = service_account.Credentials.from_service_account_file(
                    str(self.credentials_path),
                    scopes=scopes
                )
                self._service = build('drive', 'v3', credentials=self._creds, cache_discovery=False)
                print("[Google Drive] Conectado com sucesso via Service Account.")
                if trigger_precache:
                    self.trigger_background_precache()
                self._start_periodic_watcher()
            except Exception as e:
                print(f"[Google Drive] Erro ao carregar credenciais: {e}")
                self._service = None

    def _start_periodic_watcher(self):
        """Vigia o Google Drive a cada 60s em segundo plano para detectar novos lotes da Segurança."""
        if self._watcher_started:
            return
        self._watcher_started = True

        def _watcher_loop():
            while True:
                time.sleep(60)
                if self.is_live() and not self._precaching and not self._refreshing_catalog:
                    try:
                        self._refresh_catalog_in_background()
                    except Exception:
                        pass

        threading.Thread(target=_watcher_loop, daemon=True).start()

    def _refresh_catalog_in_background(self):
        """Atualiza a lista do Google Drive sem travar ou atrasar as respostas para os celulares."""
        if self._refreshing_catalog or not self.is_live():
            return

        def _bg():
            self._refreshing_catalog = True
            try:
                items = self._scan_folder_recursive(self.folder_id)
                sorted_items = sorted(items, key=lambda x: (not x.get('is_folder', False), x.get('name', '').lower()))
                self._items_cache = sorted_items
                self._items_cache_time = time.time()
                self._save_disk_catalog(sorted_items)

                # Verifica se a Segurança subiu algum arquivo novo ou editado que ainda não está no SSD
                for it in sorted_items:
                    if not it.get("is_folder") and it.get("id") not in self._failed_ids:
                        c_file = CACHE_DIR / f"{it['id']}.cached"
                        m_file = CACHE_DIR / f"{it['id']}.meta"
                        if not self._is_cache_valid(c_file, m_file, it):
                            print("\n[Vigia Drive] Novos documentos ou atualizacoes detectados no Google Drive!")
                            self.trigger_background_precache(force_scan=False)
                            break
            except Exception as e:
                print(f"[Google Drive] Aviso ao atualizar catálogo em background: {e}")
            finally:
                self._refreshing_catalog = False

        threading.Thread(target=_bg, daemon=True).start()

    def _reset_service(self):
        """Recria o cliente do Google Drive caso um socket SSL seja corrompido por concorrência."""
        try:
            if self._creds:
                self._service = build('drive', 'v3', credentials=self._creds, cache_discovery=False)
                print("[Google Drive] Conexão SSL reinicializada com sucesso.")
        except Exception as e:
            print(f"[Google Drive] Falha ao reinicializar conexão: {e}")

    def is_live(self) -> bool:
        """Retorna True se estiver conectado à API do Google Drive."""
        return self._service is not None and bool(self.folder_id)

    def set_folder_id(self, folder_id: str):
        """Permite atualizar o ID da pasta dinamicamente."""
        self.folder_id = folder_id.strip()
        self._items_cache = []
        self._items_cache_time = 0
        self._metadata_map.clear()
        self._init_service()

    def get_status(self) -> Dict[str, Any]:
        """Retorna o status atual da integração com o Google Drive."""
        if self.is_live():
            return {
                "connected": True,
                "mode": "google_drive",
                "folder_id": self.folder_id,
                "message": "Conectado ao Google Drive Oficial"
            }
        
        has_creds = self.credentials_path.exists()
        return {
            "connected": False,
            "mode": "demo_local",
            "folder_id": self.folder_id or "Não configurado",
            "has_credentials_file": has_creds,
            "message": "Rodando com arquivos locais de demonstração."
        }

    def trigger_background_precache(self, force_scan: bool = True):
        """Baixa em segundo plano para o SSD do PC os arquivos que ainda não estão em cache."""
        if self._precaching or not self.is_live():
            return

        def _worker():
            self._precaching = True
            if force_scan:
                time.sleep(2.0)  # Aguarda inicialização do servidor e primeira conexão
            try:
                print("\n[Pre-Cache] Verificando arquivos da matriz no Google Drive para armazenamento local no PC...")
                items = self.list_files(force_refresh=force_scan)
                downloadable = [it for it in items if not it.get("is_folder") and it.get("id") not in self._failed_ids]
                total = len(downloadable)
                print(f"[Pre-Cache] {total} documentos encontrados na matriz.")

                needed = []
                for item in downloadable:
                    f_id = item["id"]
                    cache_file = CACHE_DIR / f"{f_id}.cached"
                    meta_file = CACHE_DIR / f"{f_id}.meta"
                    if not self._is_cache_valid(cache_file, meta_file, item):
                        needed.append(item)

                if not needed:
                    print(f"[Pre-Cache] [OK] Todos os {total} documentos ja estao cacheados no SSD do PC! Entregas para celulares serao instantaneas.\n")
                else:
                    print(f"[Pre-Cache] Baixando {len(needed)} novos documentos da matriz para o SSD do PC...")
                    downloaded_now = 0
                    for idx, item in enumerate(needed):
                        f_id = item["id"]
                        name = item.get("name", "documento")
                        print(f" [Pre-Cache {idx+1}/{len(needed)}] Baixando: {name}...")
                        res = self.get_file_content(f_id)
                        if res:
                            downloaded_now += 1
                        else:
                            self._failed_ids.add(f_id)
                        time.sleep(0.05)

                    print(f"[Pre-Cache] [OK] Concluido! {downloaded_now} novos arquivos gravados no SSD do PC.\n")
            except Exception as e:
                print(f"[Pre-Cache] Erro durante pre-cache em segundo plano: {e}")
            finally:
                self._precaching = False

        threading.Thread(target=_worker, daemon=True).start()

    def _scan_folder_recursive(self, parent_id: str, folder_path: str = "") -> List[Dict[str, Any]]:
        """Varre uma pasta e todas as suas subpastas recursivamente no Drive com proteção thread-safe."""
        found_items = []
        page_token = None
        parent_key = "root" if parent_id == self.folder_id else parent_id
        
        while True:
            query = f"'{parent_id}' in parents and trashed = false"
            results = None
            for attempt in range(2):
                try:
                    with self._lock:
                        results = self._service.files().list(
                            q=query,
                            pageSize=100,
                            pageToken=page_token,
                            fields="nextPageToken, files(id, name, mimeType, size, modifiedTime, md5Checksum)"
                        ).execute()
                    break
                except Exception as e:
                    if ("ssl" in str(e).lower() or "record layer" in str(e).lower()) and attempt == 0:
                        self._reset_service()
                        time.sleep(0.5)
                        continue
                    raise e
            
            for item in results.get('files', []):
                mime = item.get('mimeType', '')
                original_mime = mime
                name = item.get('name', 'documento')
                item_id = item.get('id')
                
                # Se for subpasta, adiciona como pasta e desce recursivamente
                if mime == 'application/vnd.google-apps.folder':
                    sub_path = f"{folder_path}/{name}" if folder_path else name
                    found_items.append({
                        "id": item_id,
                        "name": name,
                        "mimeType": "application/vnd.google-apps.folder",
                        "parentId": parent_key,
                        "folder": folder_path or "Raiz",
                        "folder_path": folder_path,
                        "path": sub_path,
                        "modifiedTime": item.get('modifiedTime'),
                        "size": 0,
                        "size_formatted": "--",
                        "is_folder": True
                    })
                    found_items.extend(self._scan_folder_recursive(item_id, sub_path))
                elif mime in UNSUPPORTED_DRIVE_TYPES:
                    # Ignora atalhos, formulários e mapas do Google que não são arquivos binários
                    continue
                else:
                    size_bytes = int(item.get('size', 0)) if item.get('size') else 0
                    
                    # Se for documento/planilha/apresentação nativa do Google, converte extensão para PDF
                    if mime.startswith('application/vnd.google-apps.'):
                        if not name.lower().endswith('.pdf'):
                            name += '.pdf'
                        mime = 'application/pdf'

                    # Se já estiver em cache no SSD do PC, usa o tamanho real em disco (essencial para Docs exportados)
                    c_file = CACHE_DIR / f"{item_id}.cached"
                    if c_file.exists() and c_file.stat().st_size > 0:
                        if size_bytes <= 0 or original_mime.startswith('application/vnd.google-apps.'):
                            size_bytes = c_file.stat().st_size
                    
                    doc_item = {
                        "id": item_id,
                        "name": name,
                        "parentId": parent_key,
                        "folder": folder_path or "Raiz",
                        "folder_path": folder_path,
                        "mimeType": mime,
                        "originalMimeType": original_mime,
                        "size": size_bytes,
                        "size_formatted": self._format_size(size_bytes) if size_bytes > 0 else "--",
                        "modifiedTime": item.get('modifiedTime'),
                        "checksum": item.get('md5Checksum', item_id),
                        "is_folder": False
                    }
                    found_items.append(doc_item)
                    self._metadata_map[item_id] = doc_item
                    
            page_token = results.get('nextPageToken')
            if not page_token:
                break
                
        return found_items

    def _scan_local_folder_recursive(self, current_dir: Path, folder_path: str = "", parent_id: str = "root") -> List[Dict[str, Any]]:
        """Varre recursivamente o diretório local de demonstração preservando pastas e arquivos."""
        found_items = []
        if not current_dir.exists():
            return found_items

        entries = sorted(list(current_dir.iterdir()), key=lambda e: (not e.is_dir(), e.name.lower()))
        for entry in entries:
            name = entry.name
            if entry.is_dir():
                sub_path = f"{folder_path}/{name}" if folder_path else name
                folder_id = f"demo_folder_{entry.relative_to(MOCK_DIR).as_posix()}"
                found_items.append({
                    "id": folder_id,
                    "name": name,
                    "mimeType": "application/vnd.google-apps.folder",
                    "parentId": parent_id,
                    "folder": folder_path or "Raiz",
                    "folder_path": folder_path,
                    "path": sub_path,
                    "modifiedTime": datetime.fromtimestamp(entry.stat().st_mtime).isoformat(),
                    "size": 0,
                    "size_formatted": "--",
                    "is_folder": True
                })
                found_items.extend(self._scan_local_folder_recursive(entry, sub_path, parent_id=folder_id))
            elif entry.is_file():
                stat = entry.stat()
                mime, _ = mimetypes.guess_type(str(entry))
                if not mime:
                    mime = "application/octet-stream"
                
                with open(entry, "rb") as f:
                    file_hash = hashlib.md5(f.read()).hexdigest()

                file_id = f"demo_{entry.relative_to(MOCK_DIR).as_posix()}"
                item_data = {
                    "id": file_id,
                    "name": name,
                    "parentId": parent_id,
                    "folder": folder_path or "Raiz",
                    "folder_path": folder_path,
                    "mimeType": mime,
                    "size": stat.st_size,
                    "size_formatted": self._format_size(stat.st_size),
                    "modifiedTime": datetime.fromtimestamp(stat.st_mtime).isoformat(),
                    "checksum": file_hash,
                    "is_folder": False
                }
                found_items.append(item_data)
                self._metadata_map[file_id] = item_data
        return found_items

    def list_files(self, force_refresh: bool = False) -> List[Dict[str, Any]]:
        """
        Lista os arquivos e pastas com resposta INSTANTÂNEA (0ms) se já houver cache.
        Se o cache tiver mais de 45s, retorna o cache atual na hora para não travar o celular
        e dispara a atualização em segundo plano!
        """
        now = time.time()
        if not force_refresh and self._items_cache:
            if (now - self._items_cache_time >= 45) and not self._refreshing_catalog:
                self._refresh_catalog_in_background()
            return self._items_cache

        with self._lock:
            now = time.time()
            if not force_refresh and self._items_cache and (now - self._items_cache_time < 45):
                return self._items_cache

            if self.is_live():
                try:
                    items = self._scan_folder_recursive(self.folder_id)
                    sorted_items = sorted(items, key=lambda x: (not x.get('is_folder', False), x.get('name', '').lower()))
                    self._items_cache = sorted_items
                    self._items_cache_time = now
                    self._save_disk_catalog(sorted_items)
                    return sorted_items
                except Exception as e:
                    print(f"[Google Drive] Erro ao listar arquivos: {e}")
                    if self._items_cache:
                        return self._items_cache
            
            # Modo de Demonstração / Arquivos Locais
            setup_demo_files(MOCK_DIR)
            demo_items = self._scan_local_folder_recursive(MOCK_DIR)
            sorted_demo = sorted(demo_items, key=lambda x: (not x.get('is_folder', False), x.get('name', '').lower()))
            self._items_cache = sorted_demo
            self._items_cache_time = now
            return sorted_demo

    @staticmethod
    def _safe_read_cache(cache_file: Path) -> Optional[bytes]:
        """Lê o arquivo de cache com proteção contra trava temporária de leitura/escrita do Windows."""
        for _ in range(4):
            try:
                if cache_file.exists() and cache_file.stat().st_size > 0:
                    return cache_file.read_bytes()
            except OSError:
                time.sleep(0.03)
        return None

    @staticmethod
    def _is_cache_valid(cache_file: Path, meta_file: Path, known: Optional[Dict[str, Any]]) -> bool:
        """Verifica se o arquivo no SSD existe e corresponde à versão atual do Google Drive."""
        try:
            if not (cache_file.exists() and cache_file.stat().st_size > 0):
                return False
            if meta_file.exists() and known and known.get("checksum"):
                saved_hash = meta_file.read_text(encoding="utf-8").strip()
                return saved_hash == known.get("checksum")
            return True
        except OSError:
            return False

    def get_file_content(self, file_id: str) -> Optional[tuple[bytes, str, str]]:
        """
        Retorna (conteudo_bytes, mime_type, filename).
        Se já estiver em cache no disco do PC, entrega INSTANTANEAMENTE para múltiplos celulares ao mesmo tempo!
        """
        # Se for pasta, não há conteúdo direto para download
        if "folder" in file_id:
            return None

        # Se for arquivo de demo local
        if file_id.startswith("demo_"):
            rel_name = file_id[5:]
            path = MOCK_DIR / rel_name
            if path.exists() and path.is_file():
                mime, _ = mimetypes.guess_type(str(path))
                return path.read_bytes(), mime or "application/octet-stream", path.name
            
            target_name = Path(rel_name).name
            for found in MOCK_DIR.rglob(target_name):
                if found.is_file():
                    mime, _ = mimetypes.guess_type(str(found))
                    return found.read_bytes(), mime or "application/octet-stream", found.name
            return None

        # Se for do Google Drive
        if self.is_live():
            cache_file = CACHE_DIR / f"{file_id}.cached"
            meta_file = CACHE_DIR / f"{file_id}.meta"

            # 1. Recupera metadados conhecidos em memória (sem fazer chamada de rede)
            known = self._metadata_map.get(file_id)

            # 2. VERIFICAÇÃO ULTRA-RÁPIDA DO CACHE LOCAL DO PC (Leitura concorrente livre para N celulares):
            if self._is_cache_valid(cache_file, meta_file, known):
                cached_bytes = self._safe_read_cache(cache_file)
                if cached_bytes:
                    filename = known.get("name", "documento") if known else "documento"
                    mime_type = known.get("mimeType", "application/octet-stream") if known else "application/octet-stream"
                    return cached_bytes, mime_type, filename

            # 3. SE NÃO ESTÁ EM CACHE NO PC, BAIXA DO GOOGLE DRIVE COM PROTEÇÃO THREAD-SAFE
            # (Se 2 celulares pedirem o mesmo arquivo novo ao mesmo tempo, o 1º baixa e o 2º pega do SSD em 1ms!)
            with self._lock:
                known = self._metadata_map.get(file_id)
                if self._is_cache_valid(cache_file, meta_file, known):
                    cached_bytes = self._safe_read_cache(cache_file)
                    if cached_bytes:
                        filename = known.get("name", "documento") if known else "documento"
                        mime_type = known.get("mimeType", "application/octet-stream") if known else "application/octet-stream"
                        return cached_bytes, mime_type, filename

                for attempt in range(3):
                    try:
                        if known:
                            filename = known.get("name", "documento")
                            mime_type = known.get("mimeType", "application/octet-stream")
                            original_mime = known.get("originalMimeType", mime_type)
                            checksum = known.get("checksum", "")
                        else:
                            meta = self._service.files().get(
                                fileId=file_id,
                                fields="id, name, mimeType, md5Checksum"
                            ).execute()
                            filename = meta.get("name", "documento")
                            mime_type = meta.get("mimeType", "application/octet-stream")
                            original_mime = mime_type
                            checksum = meta.get("md5Checksum", "")

                        if original_mime in UNSUPPORTED_DRIVE_TYPES or mime_type in UNSUPPORTED_DRIVE_TYPES:
                            return None

                        # Documentos nativos do Google (Docs, Sheets, Slides, Drawings, etc.) requerem export_media
                        if original_mime.startswith("application/vnd.google-apps."):
                            try:
                                request = self._service.files().export_media(fileId=file_id, mimeType="application/pdf")
                                mime_type = "application/pdf"
                                if not filename.lower().endswith(".pdf"):
                                    filename += ".pdf"
                            except Exception as export_err:
                                print(f"[Google Drive] Falha ao exportar {filename}: {export_err}")
                                return None
                        else:
                            request = self._service.files().get_media(fileId=file_id)

                        fh = io.BytesIO()
                        downloader = MediaIoBaseDownload(fh, request)
                        done = False
                        while not done:
                            status, done = downloader.next_chunk()

                        content = fh.getvalue()
                        
                        # Gravação ATÔMICA e tolerante a leitura simultânea no Windows
                        try:
                            tmp_cache = CACHE_DIR / f"{file_id}_{threading.get_ident()}.cached.tmp"
                            tmp_cache.write_bytes(content)
                            tmp_cache.replace(cache_file)
                            meta_file.write_text(checksum, encoding="utf-8")
                        except OSError:
                            pass

                        if known:
                            known["size"] = len(content)
                            known["size_formatted"] = self._format_size(len(content))

                        return content, mime_type, filename

                    except Exception as e:
                        err_lower = str(e).lower()
                        if ("ssl" in err_lower or "record layer" in err_lower or "timeout" in err_lower or "429" in err_lower or "503" in err_lower) and attempt < 2:
                            self._reset_service()
                            time.sleep(0.8 * (attempt + 1))
                            continue
                        print(f"[Google Drive] Erro ao baixar arquivo {file_id}: {e}")
                        if cache_file.exists() and cache_file.stat().st_size > 0:
                            return cache_file.read_bytes(), "application/octet-stream", "arquivo"
                        return None

        return None

    @staticmethod
    def _format_size(size_bytes: int) -> str:
        """Formata bytes para KB, MB, GB."""
        if size_bytes <= 0:
            return "0 B"
        names = ("B", "KB", "MB", "GB")
        i = 0
        s = float(size_bytes)
        while s >= 1024 and i < len(names) - 1:
            s /= 1024.0
            i += 1
        return f"{s:.1f} {names[i]}"


# Instância global
drive_service = DriveService()


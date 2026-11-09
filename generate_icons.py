import zlib
import struct
from pathlib import Path

def create_png(width, height, color_bg=(37, 99, 235), color_fg=(255, 255, 255)):
    """Cria um ícone PNG válido usando apenas a biblioteca padrão do Python."""
    raw_data = bytearray()
    
    # Desenha um quadrado com bordas arredondadas e um símbolo central
    center_x, center_y = width // 2, height // 2
    radius = width * 0.35
    
    for y in range(height):
        raw_data.append(0)  # Filter byte (None)
        for x in range(width):
            dist = ((x - center_x) ** 2 + (y - center_y) ** 2) ** 0.5
            # Símbolo estilizado de sincronização / nuvem
            if dist < radius:
                # Centro contrastante
                if abs(x - center_x) < width * 0.15 and abs(y - center_y) < height * 0.05:
                    raw_data.extend(color_bg)
                elif abs(y - center_y) < height * 0.15 and abs(x - center_x) < width * 0.05:
                    raw_data.extend(color_bg)
                else:
                    raw_data.extend(color_fg)
            else:
                raw_data.extend(color_bg)
                
    # Comprime dados do IDAT
    compressed_data = zlib.compress(bytes(raw_data))
    
    # Monta os chunks PNG
    png_bytes = bytearray(b'\x89PNG\r\n\x1a\n')
    
    def make_chunk(chunk_type, data):
        length = len(data)
        crc = zlib.crc32(chunk_type + data) & 0xffffffff
        return struct.pack('>I', length) + chunk_type + data + struct.pack('>I', crc)
        
    # IHDR
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    png_bytes.extend(make_chunk(b'IHDR', ihdr_data))
    
    # IDAT
    png_bytes.extend(make_chunk(b'IDAT', compressed_data))
    
    # IEND
    png_bytes.extend(make_chunk(b'IEND', b''))
    
    return bytes(png_bytes)

icons_dir = Path(__file__).resolve().parent / "static" / "icons"
icons_dir.mkdir(parents=True, exist_ok=True)

(icons_dir / "icon-192.png").write_bytes(create_png(192, 192))
(icons_dir / "icon-512.png").write_bytes(create_png(512, 512))
print("Ícones PWA gerados com sucesso!")


from PIL import Image
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
LOGO_PATH = BASE_DIR / "static" / "logo.png"
ICONS_DIR = BASE_DIR / "static" / "icons"
ICONS_DIR.mkdir(parents=True, exist_ok=True)

def generate_icons():
    logo = Image.open(LOGO_PATH).convert("RGBA")
    
    # Gera 192x192 e 512x512 com fundo elegante ou transparente
    for size in (192, 512):
        # Cria tela quadrada com fundo escuro elegante que combina com a marca (#0b1612 ou transparente)
        # Para ícones de app mobile (maskable), um fundo sólido com padding fica perfeito
        icon = Image.new("RGBA", (size, size), (11, 22, 18, 255))
        
        # Redimensiona logo mantendo proporção com 15% de margem
        target_size = int(size * 0.8)
        logo_resized = logo.copy()
        logo_resized.thumbnail((target_size, target_size), Image.Resampling.LANCZOS)
        
        # Centraliza
        offset_x = (size - logo_resized.width) // 2
        offset_y = (size - logo_resized.height) // 2
        icon.paste(logo_resized, (offset_x, offset_y), logo_resized)
        
        icon.save(ICONS_DIR / f"icon-{size}.png", "PNG")
        print(f"Gerado: icon-{size}.png")

    # Favicon
    favicon = Image.new("RGBA", (64, 64), (11, 22, 18, 255))
    logo_fav = logo.copy()
    logo_fav.thumbnail((52, 52), Image.Resampling.LANCZOS)
    favicon.paste(logo_fav, ((64 - logo_fav.width) // 2, (64 - logo_fav.height) // 2), logo_fav)
    favicon.save(BASE_DIR / "static" / "favicon.ico", format="ICO")
    print("Gerado: favicon.ico")

if __name__ == "__main__":
    generate_icons()


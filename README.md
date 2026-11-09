# ⚡ SyncCampo - Sincronizador Offline para Equipes de Campo

Solução sob medida para resolver a dificuldade de equipes de campo ao navegar em pastas do Google Drive no celular.

Com o **SyncCampo**, o técnico abre o aplicativo no celular e toca em **um único botão**: `SINCRONIZAR TUDO AGORA`. Todos os documentos, POPs, mapas e planilhas da pasta central são salvos na memória local do celular via Service Worker/Cache PWA. No campo, mesmo sem sinal (modo avião), todos os arquivos abrem instantaneamente com 1 toque.

---

## 🌐 Acesso de Qualquer Lugar (4G / 5G / Qualquer Wi-Fi)

O SyncCampo já vem com **Cloudflare Tunnel integrado**:
- **Zero configuração de modem, roteador ou firewall** (não precisa abrir portas).
- **HTTPS com certificado SSL automático** (exigido pelos navegadores para instalar PWA e rodar offline).
- Gera uma URL pública mundial segura e um **QR Code no terminal** para os técnicos acessarem de casa, da estrada ou do campo.

---

## 🚀 Como Iniciar

Execute no terminal da pasta do projeto:
```powershell
.\.venv\Scripts\python.exe run.py
```

O script irá:
1. Conectar ao Cloudflare Tunnel automaticamente.
2. Gerar o **Link Público Mundial HTTPS** (ex: `https://xxxx.trycloudflare.com`).
3. Desenhar o **QR Code no terminal**.
4. Iniciar o servidor de arquivos.

> Basta enviar esse link para a equipe no WhatsApp ou pedir para apontarem a câmera do celular para o QR Code na sua tela!

---

## 📱 Como a Equipe Usa no Celular

1. O técnico abre o link recebido no navegador (Chrome no Android ou Safari no iOS).
2. Toca no aviso **"Instalar no Celular"** (ou no menu do navegador: *Adicionar à Tela Inicial*). O SyncCampo vira um ícone de app nativo.
3. Antes de sair para a área sem cobertura, clica em **"SINCRONIZAR TUDO AGORA"**.
4. Uma barra de progresso baixa todos os arquivos para a memória do celular.
5. **No campo:** Pode desligar os dados móveis ou entrar em modo avião. Ao abrir o app, os arquivos têm a marcação **"✓ Salvo Offline"** e abrem imediatamente ao clicar.

---

## ☁️ Conectando com a Pasta Real do Google Drive

Por padrão, o app já vem com **arquivos de demonstração reais** para você testar imediatamente. Para sincronizar uma pasta de verdade do Google Drive:

### Passo 1: Criar uma Conta de Serviço (Google Cloud)
1. Acesse o [Google Cloud Console](https://console.cloud.google.com/).
2. Crie um projeto novo (ex: `SyncCampo`).
3. Vá em **APIs e Serviços** > **Biblioteca**, busque por **Google Drive API** e clique em **Ativar**.
4. Vá em **APIs e Serviços** > **Credenciais** > **Criar Credenciais** > **Conta de Serviço**.
5. Crie uma chave no formato **JSON** e baixe o arquivo.
6. Renomeie o arquivo baixado para **`service_account.json`** e coloque-o na pasta raiz deste projeto (`sync-campo/`).

### Passo 2: Compartilhar a Pasta do Drive
1. Abra o arquivo `service_account.json` e copie o endereço de e-mail do campo `"client_email"` (ex: `sync-campo@meu-projeto.iam.gserviceaccount.com`).
2. No seu Google Drive, clique com o botão direito na **pasta central** que a equipe precisa acessar > **Compartilhar**.
3. Adicione o e-mail da conta de serviço como **Leitor**.

### Passo 3: Configurar o ID da Pasta
1. Copie o ID da pasta (a sequência no final da URL do Drive):
   `https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9...` -> o ID é `1a2b3c4d5e6f7g8h9...`
2. Crie um arquivo `.env` (ou copie de `.env.example`) e preencha:
   ```env
   GOOGLE_DRIVE_FOLDER_ID=1a2b3c4d5e6f7g8h9...
   ```
3. Reinicie o servidor: `.\.venv\Scripts\python.exe run.py`. Pronto!

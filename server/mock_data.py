"""
Gera arquivos de exemplo reais voltados para a operação da RS Florestal
(Silvicultura, Colheita, Transporte, Manutenção e Segurança de Campo).
"""
from pathlib import Path
from datetime import datetime

def generate_rich_os_pdf(pdf_path: Path):
    """Gera uma Ordem de Serviço Florestal profissional e rica visualmente."""
    try:
        from reportlab.lib.pagesizes import letter
        from reportlab.lib import colors
        from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable
        from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
        
        doc = SimpleDocTemplate(
            str(pdf_path),
            pagesize=letter,
            rightMargin=36,
            leftMargin=36,
            topMargin=36,
            bottomMargin=36
        )
        
        styles = getSampleStyleSheet()
        
        # Estilos Customizados RS Florestal
        title_style = ParagraphStyle(
            'HeaderTitle',
            parent=styles['Heading1'],
            fontName='Helvetica-Bold',
            fontSize=18,
            textColor=colors.HexColor('#14221c'),
            spaceAfter=4
        )
        
        subtitle_style = ParagraphStyle(
            'HeaderSub',
            parent=styles['Normal'],
            fontName='Helvetica',
            fontSize=11,
            textColor=colors.HexColor('#2e664c'),
            spaceAfter=12
        )
        
        section_style = ParagraphStyle(
            'SectionHeader',
            parent=styles['Heading2'],
            fontName='Helvetica-Bold',
            fontSize=12,
            textColor=colors.HexColor('#15803d'),
            spaceBefore=10,
            spaceAfter=6
        )
        
        cell_bold = ParagraphStyle(
            'CellBold',
            parent=styles['Normal'],
            fontName='Helvetica-Bold',
            fontSize=9,
            textColor=colors.HexColor('#1f2937')
        )
        
        cell_normal = ParagraphStyle(
            'CellNorm',
            parent=styles['Normal'],
            fontName='Helvetica',
            fontSize=9,
            textColor=colors.HexColor('#374151')
        )
        
        elements = []
        
        # Cabeçalho da Empresa
        elements.append(Paragraph("🌲 RS FLORESTAL - OPERAÇÕES DE CAMPO", title_style))
        elements.append(Paragraph("ORDEM DE SERVIÇO Nº <b>OS-2026/0482</b> | SILVICULTURA E PLANTIO", subtitle_style))
        elements.append(HRFlowable(width="100%", thickness=2, color=colors.HexColor('#22c55e'), spaceAfter=14))
        
        # Tabela 1: Dados Gerais do Talhão
        elements.append(Paragraph("1. LOCALIZAÇÃO E DADOS DO TALHÃO", section_style))
        t1_data = [
            [Paragraph("Unidade / Fazenda:", cell_bold), Paragraph("Horto Florestal Sul - Gleba 2", cell_normal), Paragraph("Data Programada:", cell_bold), Paragraph(datetime.now().strftime("%d/%m/%Y"), cell_normal)],
            [Paragraph("Talhão:", cell_bold), Paragraph("Talhão 14 (Setor B)", cell_normal), Paragraph("Área Total:", cell_bold), Paragraph("51,40 Hectares", cell_normal)],
            [Paragraph("Cultura:", cell_bold), Paragraph("Eucalyptus urograndis (Clone H13)", cell_normal), Paragraph("Espaçamento:", cell_bold), Paragraph("3,5 m x 2,0 m (1.428 mudas/ha)", cell_normal)],
            [Paragraph("Topografia:", cell_bold), Paragraph("Ondulada suave (0 - 12%)", cell_normal), Paragraph("Tipo de Solo:", cell_bold), Paragraph("Latossolo Vermelho-Amarelo", cell_normal)]
        ]
        t1 = Table(t1_data, colWidths=[120, 160, 110, 150])
        t1.setStyle(TableStyle([
            ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#f0fdf4')),
            ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#bbf7d0')),
            ('PADDING', (0,0), (-1,-1), 5),
            ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ]))
        elements.append(t1)
        elements.append(Spacer(1, 10))
        
        # Tabela 2: Atividades Operacionais
        elements.append(Paragraph("2. ATIVIDADES E METAS OPERACIONAIS", section_style))
        t2_data = [
            [Paragraph("Atividade", cell_bold), Paragraph("Equipamento / Insumo", cell_bold), Paragraph("Dosagem / Rendimento", cell_bold), Paragraph("Status", cell_bold)],
            [Paragraph("Subsolagem Profunda (50 cm)", cell_normal), Paragraph("Trator JD 6115J + Subsolador 1 haste", cell_normal), Paragraph("1,8 ha / hora", cell_normal), Paragraph("Liberado", cell_bold)],
            [Paragraph("Adubação de Base no Sulco", cell_normal), Paragraph("NPK 06-30-06 + 0,5% Boro + Zinco", cell_normal), Paragraph("180 kg / ha", cell_normal), Paragraph("Abastecido", cell_bold)],
            [Paragraph("Distribuição de Mudas", cell_normal), Paragraph("Carreta Tracionada 4x4 + Caçamba", cell_normal), Paragraph("73.400 mudas total", cell_normal), Paragraph("Em transporte", cell_bold)],
            [Paragraph("Plantio Manual / Hidrogel", cell_normal), Paragraph("Matraca Florestal + Gel Polímero", cell_normal), Paragraph("1,5 L calda por muda", cell_normal), Paragraph("Frente Ativa", cell_bold)]
        ]
        t2 = Table(t2_data, colWidths=[150, 180, 130, 80])
        t2.setStyle(TableStyle([
            ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#166534')),
            ('TEXTCOLOR', (0,0), (-1,0), colors.white),
            ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
            ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#f8fafc')]),
            ('PADDING', (0,0), (-1,-1), 5),
            ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ]))
        # Ajusta cor do cabeçalho da tabela 2
        for i in range(4):
            t2_data[0][i].style.textColor = colors.white
        elements.append(t2)
        elements.append(Spacer(1, 10))
        
        # Tabela 3: Equipe Responsável e Segurança
        elements.append(Paragraph("3. EQUIPE DESIGNADA E CONTROLES DE SEGURANÇA", section_style))
        t3_data = [
            [Paragraph("Encarregado de Campo:", cell_bold), Paragraph("Carlos Eduardo Silva (Rádio Ch 4)", cell_normal)],
            [Paragraph("Operador Tratorista:", cell_bold), Paragraph("Marcos Roberto Oliveira (JD 6115J)", cell_normal)],
            [Paragraph("Técnico de Segurança:", cell_bold), Paragraph("Plantão CCO Florestal - (11) 98765-4321", cell_normal)],
            [Paragraph("Orientações de Segurança:", cell_bold), Paragraph("Obrigatório uso de perneiras, bota bico de aço, chapéu com aba e protetor auricular. Respeitar raio de 30m do maquinário.", cell_normal)],
            [Paragraph("Preservação Ambiental:", cell_bold), Paragraph("Respeitar estritamente faixa de 30 metros de APP do Córrego das Antas. Proibido abastecimento de trator no talhão.", cell_normal)]
        ]
        t3 = Table(t3_data, colWidths=[150, 390])
        t3.setStyle(TableStyle([
            ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#f8fafc')),
            ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#e2e8f0')),
            ('PADDING', (0,0), (-1,-1), 5),
            ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ]))
        elements.append(t3)
        elements.append(Spacer(1, 14))
        
        # Rodapé de validação
        elements.append(HRFlowable(width="100%", thickness=1, color=colors.HexColor('#94a3b8'), spaceAfter=8))
        elements.append(Paragraph("<font size=8 color='#64748b'>Documento sincronizado eletronicamente via Sync RS Florestal. Válido para execução em campo sem sinal.</font>", styles['Normal']))
        
        doc.build(elements)
        print("PDF rico de OS gerado com sucesso!")
        return True
    except Exception as e:
        print(f"Erro ao gerar PDF com reportlab: {e}")
        return False


def setup_demo_files(mock_dir: Path):
    mock_dir.mkdir(parents=True, exist_ok=True)
    
    # Subpastas de exemplo para demonstrar a estrutura do Google Drive
    folder_os = mock_dir / "01. Ordens de Serviço (OS)"
    folder_pop = mock_dir / "02. Procedimentos Operacionais (POP)"
    folder_mapas = mock_dir / "03. Mapas Florestais"
    
    folder_os.mkdir(parents=True, exist_ok=True)
    folder_pop.mkdir(parents=True, exist_ok=True)
    folder_mapas.mkdir(parents=True, exist_ok=True)
    
    # 1. Arquivos fora das pastas (na Raiz do Drive)
    # 1.1 Checklist Diário de Tratores e Máquinas Florestais (CSV/Planilha)
    checklist_file = mock_dir / "Checklist_Diario_Tratores_e_Maquinas.csv"
    if not checklist_file.exists():
        checklist_file.write_text(
            "Equipamento,Item Verificado,Criterio Minimo,Status Campo\n"
            "Trator Florestal,Nivel Oleo Motor,Entre min e max,OK\n"
            "Trator Florestal,Filtro de Ar,Limpo / Sem saturacao,OK\n"
            "Trator Florestal,Pneus Florestais / Garras,Pressao 28 PSI,OK\n"
            "Caminhonete 4x4,Guincho e Cinta de Reboque,Inspecionado,OK\n"
            "Equipe Campo,Radio Comunicador VHF,Bateria 100%,OK\n"
            "Equipe Campo,Kit Primeiros Socorros,Lacrado / Valido,OK\n"
            "Geral,Extintor de Incendio 4kg,Manometro Verde (OK),OK\n",
            encoding="utf-8"
        )
        
    # 1.2 Contatos de Emergência e Apoio Florestal (Texto na Raiz)
    contatos_file = mock_dir / "Contatos_Emergencia_Bases_e_Brigada.txt"
    if not contatos_file.exists():
        contatos_file.write_text(
            "=== RS FLORESTAL - CONTATOS DE EMERGÊNCIA E BASES ===\n\n"
            "- Central de Controle Operacional (CCO): (11) 3456-7890\n"
            "- Brigada de Incêndio Florestal: Canal 1 VHF ou (11) 99111-2233\n"
            "- Resgate / Bombeiros: 193\n"
            "- SAMU / Emergência Médica: 192\n"
            "- Mecânica Pesada / Apoio no Horto: (11) 99444-5566\n"
            "- Gestor de Operações Florestais: (11) 98888-7766\n",
            encoding="utf-8"
        )

    # 2. Arquivos dentro das pastas
    # 2.1 POP de Silvicultura e Segurança Florestal (dentro de '02. Procedimentos Operacionais (POP)')
    pop_file = folder_pop / "POP_01_Seguranca_e_Operacao_Silvicultura.txt"
    if not pop_file.exists():
        pop_file.write_text(
            "=== RS FLORESTAL - PROCEDIMENTO OPERACIONAL DE CAMPO (POP-01) ===\n\n"
            "Atividade: Silvicultura, Preparo de Solo e Plantio\n\n"
            "1. EPIs Obrigatórios no Horto Florestal:\n"
            "   - Capacete com jugular e protetor auricular\n"
            "   - Perneira de proteção contra animais peçonhentos\n"
            "   - Botina de segurança com biqueira de aço\n"
            "   - Camisa manga longa com proteção UV e óculos de sol/segurança\n\n"
            "2. Procedimento de Saída para o Talhão:\n"
            "   - Conferir nível de água potável no comboio / viatura\n"
            "   - Verificar kit de primeiros socorros e antiofídico\n"
            "   - Conferir pressão e estepe de tratores / caminhonetes\n"
            "   - Sincronizar todos os mapas de talhão e ordens de serviço no Sync RS Florestal antes de sair da base!\n\n"
            "3. Comunicação e Emergência Florestal:\n"
            "   - Canal de Rádio VHF: 154.570 MHz (Canal 4 - Operações Florestais)\n"
            "   - Brigada de Incêndio / CCO: (11) 98765-4321\n",
            encoding="utf-8"
        )

    # 2.2 Ordem de Serviço Florestal (PDF profissional dentro de '01. Ordens de Serviço (OS)')
    pdf_file = folder_os / "OS_Planejamento_Talhao_Setor_Sul.pdf"
    if not pdf_file.exists() or pdf_file.stat().st_size < 1000:
        generate_rich_os_pdf(pdf_file)

    # 2.3 Mapa de Talhões Florestais (dentro de '03. Mapas Florestais')
    mapa_file = folder_mapas / "Mapa_Talhoes_Eucalipto_Horto_Sul.svg"
    if not mapa_file.exists():
        svg_content = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 520" width="100%" height="100%">
  <rect width="800" height="520" fill="#0d1b15"/>
  <text x="400" y="45" font-family="Arial, sans-serif" font-size="22" fill="#4ade80" text-anchor="middle" font-weight="bold">RS FLORESTAL - MAPA DE TALHÕES (HORTO SUL)</text>
  
  <!-- Talhão 12 -->
  <polygon points="120,90 320,90 300,240 90,230" fill="#1b4332" stroke="#4ade80" stroke-width="2"/>
  <text x="200" y="165" font-family="Arial, sans-serif" font-size="14" fill="#ffffff" font-weight="bold" text-anchor="middle">TALHÃO 12</text>
  <text x="200" y="185" font-family="Arial, sans-serif" font-size="11" fill="#86efac" text-anchor="middle">Eucalipto Urograndis - 42 ha</text>
  
  <!-- Talhão 13 -->
  <polygon points="340,90 560,100 540,250 320,240" fill="#2d6a4f" stroke="#4ade80" stroke-width="2"/>
  <text x="430" y="165" font-family="Arial, sans-serif" font-size="14" fill="#ffffff" font-weight="bold" text-anchor="middle">TALHÃO 13</text>
  <text x="430" y="185" font-family="Arial, sans-serif" font-size="11" fill="#86efac" text-anchor="middle">Preparo de Solo - 38 ha</text>
  
  <!-- Talhão 14 (Em Operação) -->
  <polygon points="580,105 740,110 720,270 560,255" fill="#40916c" stroke="#84cc16" stroke-width="3"/>
  <text x="645" y="185" font-family="Arial, sans-serif" font-size="14" fill="#ffffff" font-weight="bold" text-anchor="middle">TALHÃO 14</text>
  <text x="645" y="205" font-family="Arial, sans-serif" font-size="11" fill="#d9f99d" text-anchor="middle">Plantio Ativo - 51 ha</text>

  <!-- Estrada Florestal -->
  <path d="M 60,310 Q 250,280 400,340 T 750,330" fill="none" stroke="#d97706" stroke-width="6" stroke-dasharray="10,6"/>
  <text x="400" y="375" font-family="Arial, sans-serif" font-size="12" fill="#fbbf24" text-anchor="middle">Estrada de Tráfego de Caminhões / Comboio</text>

  <!-- Área de Preservação / APP -->
  <rect x="90" y="400" width="630" height="70" rx="8" fill="#143126" stroke="#2d6a4f" stroke-width="1.5"/>
  <text x="110" y="430" font-family="Arial, sans-serif" font-size="13" fill="#86efac" font-weight="bold">ÁREA DE PRESERVAÇÃO PERMANENTE (APP) - Córrego das Antas</text>
  <text x="110" y="450" font-family="Arial, sans-serif" font-size="11" fill="#94a3b8">Proibida circulação de maquinário pesado fora do leito da estrada.</text>
</svg>"""
        mapa_file.write_text(svg_content, encoding="utf-8")

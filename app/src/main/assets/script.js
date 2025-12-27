/* ========================================================
   1. Configuração e Utilitários Globais
   ======================================================== */

// Variáveis para as bibliotecas carregadas dinamicamente
let pdfjsLib = null;
let PDFLib = null;

// Mapa para gerenciar promessas de OCR (ID -> Função de Resolução)
const ocrPromises = {};

/**
 * Função chamada pelo JAVA (Android) quando o OCR termina.
 * @param {string} callbackId - O ID da requisição.
 * @param {string} text - O texto extraído.
 */
function onOcrResult(callbackId, text) {
    if (ocrPromises[callbackId]) {
        console.log(`[JS] Retorno de OCR recebido para ID: ${callbackId}`);
        ocrPromises[callbackId](text); // Resolve a promessa pendente
        delete ocrPromises[callbackId]; // Limpa memória
    }
}

/**
 * Solicita ao Android que faça o OCR de uma imagem Base64.
 * @param {string} base64Image - A imagem da página.
 * @returns {Promise<string>} - O texto encontrado.
 */
function performAndroidOCR(base64Image) {
    return new Promise((resolve) => {
        if (window.Android && window.Android.performOCR) {
            const callbackId = 'ocr_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
            ocrPromises[callbackId] = resolve;
            const cleanBase64 = base64Image.replace(/^data:image\/(png|jpg|jpeg);base64,/, "");
            window.Android.performOCR(cleanBase64, callbackId);
        } else {
            console.warn("Interface OCR Nativa não encontrada. Rodando em navegador comum?");
            resolve("");
        }
    });
}

/**
 * Envia o PDF final para o Android salvar via MediaStore.
 */
function nativeDownload(fileName, blob) {
    const reader = new FileReader();
    reader.onload = function(event) {
        const base64Data = event.target.result.split(',')[1];
        if (window.Android && typeof window.Android.downloadPdf === 'function') {
            window.Android.downloadPdf(base64Data, fileName);
        } else {
            console.log("Download via navegador (fallback)");
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = fileName;
            a.click();
        }
    };
    reader.readAsDataURL(blob);
}

// Funções de UI
function displayLogMessage(msg) {
    const el = document.getElementById("log-messages");
    if (el) el.innerText = msg;
    console.log("[LogUI]", msg);
}

function updateFileName() {
    const fileInput = document.getElementById("pdfUpload");
    const nameSpan = document.getElementById("file-selected-name");
    const btn = document.getElementById("processarPDF");
    const container = document.getElementById("pdf-pages-container");

    const allFiles = fileInput.files;
    const pdfFiles = Array.from(allFiles).filter(f => f.type === 'application/pdf');

    if (allFiles.length > 0 && pdfFiles.length === 0) {
        alert("Nenhum arquivo PDF válido selecionado. Apenas arquivos PDF são permitidos.");
        fileInput.value = ""; // Limpa a seleção
        nameSpan.textContent = 'Nenhum arquivo selecionado';
        if (btn) btn.disabled = true;
        return;
    }

    if (allFiles.length > pdfFiles.length) {
        alert('Alguns arquivos não eram PDFs e foram ignorados.');
    }

    if (pdfFiles.length > 0) {
        nameSpan.textContent = pdfFiles.length === 1 ?
            pdfFiles[0].name : `${pdfFiles.length} arquivos selecionados`;
        if (btn) btn.disabled = false;
        if (container) container.innerHTML = "";
        displayLogMessage("Pronto para processar.");
    } else {
        nameSpan.textContent = "Nenhum arquivo selecionado";
        if (btn) btn.disabled = true;
    }
}

function scrollToResults() {
    const container = document.getElementById("pdf-pages-container");
    if (container) {
        container.scrollIntoView({ behavior: "smooth", block: "start" });
    }
}

// Menu Lateral
function openNav() { document.getElementById("mySidenav").style.width = "250px"; }
function closeNav() { document.getElementById("mySidenav").style.width = "0"; }
function exitApp() {
    if (window.Android && typeof window.Android.exitApp === 'function') {
        window.Android.exitApp();
    }
}

/* ========================================================
   2. Lógica de Extração de Nomes (Inteligência Melhorada)
   ======================================================== */

function extractNameInfo(textToSearch, pageNumber, source = "Texto") {
    if (!textToSearch || textToSearch.length < 5) return { nome: null };

    // Tratamento para garantir que quebras de linha literais (\\n) vindas do Java sejam tratadas como quebras reais
    // E removemos múltiplos espaços para facilitar o Regex
    const cleanText = textToSearch.replace(/\\n/g, '\n').replace(/\r/g, '');
    const lines = cleanText.split('\n').map(l => l.trim()).filter(l => l.length > 0);

    // Padrões específicos para Informe de Rendimentos e Documentos Fiscais
    const patterns = [
        // Prioridade Alta: Termos exatos de Informe de Rendimentos
        { reg: /Nome.*da.*Fonte.*Pagadora/i, nextLine: true, type: 'IR_PJ' },
        { reg: /Pessoa.*F[ií]sica.*Benefici[áa]ria/i, nextLine: true, type: 'IR_PF' },
        { reg: /Benefici[áa]rio.*dos.*Rendimentos/i, nextLine: true, type: 'IR_PF' },

        // Prioridade Média: Padrões comuns
        { reg: /Nome\s*do\s*Benefici[áa]rio\s*[:\-]?\s*(.+)/i, type: 'Geral' },
        { reg: /Nome\s*da\s*Fonte\s*Pagadora\s*[:\-]?\s*(.+)/i, type: 'Geral' },
        { reg: /Raz[ãa]o\s*Social\s*[:\-]?\s*(.+)/i, type: 'PJ' },
        { reg: /Nome\s*Empresarial\s*[:\-]?\s*(.+)/i, type: 'PJ' },

        // Busca direta na mesma linha (ex: Nome: Fulano)
        { reg: /^Nome\s*[:\-]\s*(.+)/i, type: 'Simples' },
        { reg: /^Benefici[áa]rio\s*[:\-]\s*(.+)/i, type: 'Simples' }
    ];

    console.log(`[Pág ${pageNumber}] Iniciando busca de nomes em ${lines.length} linhas.`);

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];

        for (const p of patterns) {
            let possibleName = null;

            if (p.nextLine) {
                // Se achou o cabeçalho (ex: "Nome do Beneficiário"), pega a próxima linha que não seja um rótulo
                if (p.reg.test(line) && i + 1 < lines.length) {
                    // Pula linhas vazias ou que pareçam apenas números/CNPJ solto
                    let offset = 1;
                    while (i + offset < lines.length) {
                        const nextLineCandidate = lines[i + offset];
                        // Se a próxima linha for muito curta ou for um CPF/CNPJ solto, pula
                        if (nextLineCandidate.length > 3 && !/^(CPF|CNPJ|DATA|VALOR)/i.test(nextLineCandidate)) {
                            possibleName = nextLineCandidate;
                            break;
                        }
                        offset++;
                        if (offset > 3) break; // Não busca muito longe
                    }
                }
            } else {
                // Captura na mesma linha (Grupo 1 do regex)
                const match = line.match(p.reg);
                if (match && match[1]) possibleName = match[1];
            }

            if (possibleName) {
                // Limpeza agressiva do nome encontrado
                let cleanName = cleanNameStr(possibleName);

                if (isValidName(cleanName)) {
                    console.log(`[Pág ${pageNumber}] SUCESSO via ${source} (${p.type}): "${cleanName}" (Bruto: "${possibleName}")`);
                    return { nome: cleanName };
                }
            }
        }
    }

    // TENTATIVA DE DESESPERO: Procurar por linhas que pareçam nomes (tudo maiúsculo, sem números, longo)
    // Útil para OCRs que perdem o rótulo "Nome"
    for (const line of lines) {
        if (/^[A-Z\s]{10,}$/.test(line)) { // Linha com apenas letras maiúsculas e espaços, min 10 chars
            let clean = cleanNameStr(line);
            if (isValidName(clean) && !/(IMPOSTO|RENDA|FEDERAL|RECEITA|BRASIL|MINISTERIO|COMPROVANTE)/i.test(clean)) {
                 console.log(`[Pág ${pageNumber}] Nome inferido por caixa alta: "${clean}"`);
                 return { nome: clean };
            }
        }
    }

    return { nome: null };
}

// Remove sufixos como CPF, CNPJ, Datas que o OCR possa ter lido na mesma linha
function cleanNameStr(str) {
    if (!str) return "";
    // Remove tudo após palavras chave como CPF, CNPJ se estiverem na mesma linha
    str = str.split(/(CPF|CNPJ|CNPJ\/CPF|Valores|Pagamento)/i)[0];
    // Remove digitos no final ou soltos
    str = str.replace(/\d{2,}\.?\d{3}\.?\d{3}[\/\-]?\d{0,4}-?\d{2}/g, '');
    // Remove pontuação final
    return str.replace(/[\.\-:,;]+$/, '').trim();
}

function isValidName(name) {
    if (!name || name.length < 4) return false;
    const clean = name.toUpperCase();

    // Se tiver apenas números e simbolos
    if (/^[\d\s\.\-\/]+$/.test(clean)) return false;

    // Palavras proibidas que indicam que NÃO é um nome de pessoa/empresa, mas sim um rótulo do documento
    const forbidden = [
        'DATA', 'VALOR', 'TOTAL', 'RENDIMENTO', 'IMPOSTO', 'LIQUIDO',
        'RETIDO', 'FONTE', 'PAGADORA', 'BENEFICIARIA', 'EXERCICIO',
        'ANEXO', 'PAGINA', 'DECLARACAO', 'ASSINATURA', 'CPF', 'CNPJ',
        'OBSERVACAO', 'REAIS', 'CENTAVOS'
    ];

    // Se o nome for EXATAMENTE uma das palavras proibidas
    if (forbidden.includes(clean)) return false;

    // Se contiver palavras muito fortes de estrutura
    if (clean.includes("MINISTERIO") || clean.includes("SECRETARIA")) return false;

    return true;
}

/* ========================================================
   3. Processamento Principal
   ======================================================== */

async function processarPagina(pdfJsDoc, pdfLibDoc, pageNum, pageIndex, total) {
    let nomeIdentificado = null;
    try {
        displayLogMessage(`Processando pág ${pageNum}/${total}...`);
        const page = await pdfJsDoc.getPage(pageNum);

        // 1. Tenta extrair texto digital (camada de texto do PDF)
        try {
            const textContent = await page.getTextContent();
            // Junta as strings, preservando alguma formatação básica
            const textStr = textContent.items.map(s => s.str).join('\n');

            if (textStr.length > 50) {
                const info = extractNameInfo(textStr, pageNum, "Texto Digital");
                nomeIdentificado = info.nome;
            }
        } catch(e) {
            console.error("Erro ao ler texto digital", e);
        }

        // 2. Se não achou nome no texto digital, tenta OCR (Imagem)
        if (!nomeIdentificado) {
            displayLogMessage(`Aplicando OCR na pág ${pageNum}...`);
            const viewport = page.getViewport({ scale: 2.0 }); // Aumentei a escala para melhorar leitura do OCR
            const canvas = document.createElement("canvas");
            canvas.width = viewport.width;
            canvas.height = viewport.height;
            const ctx = canvas.getContext("2d");

            await page.render({ canvasContext: ctx, viewport: viewport }).promise;

            // Qualidade 0.9 para melhor nitidez no OCR
            const base64Image = canvas.toDataURL('image/jpeg', 0.9);

            // Chama o Android/Java
            const ocrText = await performAndroidOCR(base64Image);

            if (ocrText && ocrText.length > 10) {
                const infoOcr = extractNameInfo(ocrText, pageNum, "OCR Nativo");
                nomeIdentificado = infoOcr.nome;
            }
        }

        return { pageIndex, pageNum, nomeIdentificado, pdfLibDoc };
    } catch (err) {
        console.error("Erro processamento pág " + pageNum, err);
        return { pageIndex, pageNum, nomeIdentificado: null, pdfLibDoc };
    }
}

async function processarPdf() {
    const fileInput = document.getElementById("pdfUpload");
    const container = document.getElementById("pdf-pages-container");
    const btn = document.getElementById("processarPDF");

    const pdfFiles = Array.from(fileInput.files).filter(f => f.type === 'application/pdf');

    if (pdfFiles.length === 0) {
        alert("Por favor, selecione pelo menos um arquivo PDF válido.");
        return;
    }

    btn.disabled = true;
    btn.textContent = "Processando...";
    container.innerHTML = ""; // Limpa resultados anteriores

    try {
        let allResults = [];

        // 1. Processa todas as páginas de todos os arquivos
        for (const file of pdfFiles) {
            displayLogMessage(`Lendo arquivo: ${file.name}`);
            const buffer = await file.arrayBuffer();

            // Carrega documento para manipulação (pdf-lib) e leitura (pdf.js)
            const pdfLibDoc = await PDFLib.PDFDocument.load(buffer);
            const pdfJsDoc = await pdfjsLib.getDocument({ data: new Uint8Array(buffer) }).promise;

            const numPages = pdfLibDoc.getPageCount();

            for (let i = 0; i < numPages; i++) {
                // pageNum é i+1 (humano), pageIndex é i (zero-based)
                const res = await processarPagina(pdfJsDoc, pdfLibDoc, i + 1, i, numPages);
                allResults.push(res);
            }
        }

        // 2. Agrupamento Inteligente (Lógica de Continuação)
        const groups = {};
        const UNKNOWN = "Outros_Documentos"; // Nome padrão se nada for achado
        let lastValidName = null; // Guarda o último nome achado para páginas de continuação

        allResults.forEach(p => {
            let groupName = p.nomeIdentificado;

            if (groupName) {
                // Se achou um nome nesta página, atualiza o "último válido"
                lastValidName = groupName;
            } else {
                // Se não achou nome, tenta usar o da página anterior (continuação do IR)
                if (lastValidName) {
                    groupName = lastValidName;
                    console.log(`[Pág ${p.pageNum}] Sem nome identificado. Assumindo continuação de: "${lastValidName}"`);
                } else {
                    groupName = UNKNOWN;
                    console.log(`[Pág ${p.pageNum}] Sem nome e sem anterior. Movido para: "${UNKNOWN}"`);
                }
            }

            if (!groups[groupName]) groups[groupName] = [];
            groups[groupName].push(p);
        });

        displayLogMessage("Gerando arquivos finais...");

        // 3. Geração dos PDFs Finais e Criação dos Botões
        for (const [nome, paginas] of Object.entries(groups)) {
            if (paginas.length === 0) continue;

            // Cria um novo PDF
            const newPdf = await PDFLib.PDFDocument.create();

            // Copia as páginas do PDF original para o novo
            // Nota: Precisamos agrupar por documento de origem para copiar corretamente
            // Mas se for apenas um arquivo de upload, todos vêm do mesmo pdfLibDoc
            for (const p of paginas) {
                const [cp] = await newPdf.copyPages(p.pdfLibDoc, [p.pageIndex]);
                newPdf.addPage(cp);
            }

            const pdfBytes = await newPdf.save();
            const blob = new Blob([pdfBytes], { type: 'application/pdf' });

            // Sanitiza o nome do arquivo para evitar caracteres inválidos no Android
            const safeName = nome.replace(/[^a-zA-Z0-9_\- áàãâéêíóõôúçÁÀÃÂÉÊÍÓÕÔÚÇ]/g, '').trim() + ".pdf";

            console.log(`[JS] Preparando download: "${safeName}" com ${paginas.length} páginas.`);

            // Criação do Elemento Visual
            const div = document.createElement("div");
            div.className = "custom-pdf-page-item";
            div.style.cssText = "margin: 10px 0; padding: 15px; border: 1px solid #ddd; border-radius: 8px; background-color: #f9f9f9; display: flex; justify-content: space-between; align-items: center;";

            // Texto do item
            const label = document.createElement("div");
            label.innerHTML = `<strong>${safeName}</strong><br><small>${paginas.length} página(s)</small>`;

            // Botão de Download
            const b = document.createElement("button");
            b.className = "button1";
            b.style.padding = "8px 16px";
            b.textContent = "Baixar";
            b.onclick = () => {
                b.disabled = true;
                b.textContent = "Salvando...";
                nativeDownload(safeName, blob);

                // Reativa o botão após 3 segundos
                setTimeout(() => {
                    b.disabled = false;
                    b.textContent = "Baixar Novamente";
                }, 3000);
            };

            div.appendChild(label);
            div.appendChild(b);
            container.appendChild(div);
        }

        displayLogMessage("Processamento concluído!");
        scrollToResults();

    } catch (e) {
        console.error(e);
        displayLogMessage("Erro Crítico: " + e.message);
        alert("Ocorreu um erro no processamento.\n" + e.message);
    } finally {
        btn.disabled = false;
        btn.textContent = "Processar PDF";
    }
}

function updateUI(translations) {
    document.querySelectorAll('[data-i18n]').forEach(element => {
        const key = element.getAttribute('data-i18n');
        if (translations[key]) {
            element.textContent = translations[key];
        }
    });
}

function changeLanguage(lang) {
    if (window.Android && window.Android.getTranslations) {
        const translationsJson = window.Android.getTranslations(lang);
        try {
            const translations = JSON.parse(translationsJson);
            updateUI(translations);
        } catch (e) {
            console.error("Error parsing translations from Android", e);
        }
    }
}


function loadScript(src, cb) {
    const s = document.createElement('script');
    s.src = src;
    s.onload = cb;
    s.onerror = () => displayLogMessage("Erro ao carregar " + src);
    document.head.appendChild(s);
}

document.addEventListener("DOMContentLoaded", () => {
    const btn = document.getElementById("processarPDF");
    if (btn) btn.disabled = true;
    displayLogMessage("Carregando sistema...");
    loadScript("libs/pdf/pdf.min.js", () => {
        pdfjsLib = window.pdfjsLib;
        pdfjsLib.GlobalWorkerOptions.workerSrc = "libs/pdf/pdf.worker.min.js";
        loadScript("libs/pdf-lib/pdf-lib.min.js", () => {
            PDFLib = window.PDFLib;
            if (btn) btn.disabled = false;
            displayLogMessage("Pronto para processar. Selecione arquivos PDF.");
            document.getElementById("processarPDF").addEventListener("click", processarPdf);
            const upload = document.getElementById("pdfUpload");
            if(upload) upload.addEventListener("change", updateFileName);
        });
    });

    const userLang = navigator.language || navigator.userLanguage;
    changeLanguage(userLang.split('-')[0]);
});

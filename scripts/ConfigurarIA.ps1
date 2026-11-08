$ErrorActionPreference = 'Stop'
$taskPastaProjeto = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ChaveIA.ps1')
$taskChaveDigitada = $null
try {
    Write-Host 'ORKUT 2.0 | Configurar a IA neste computador' -ForegroundColor Magenta
    Write-Host 'Cole sua chave da Groq abaixo. Ela ficara oculta e sera salva com a protecao da sua conta do Windows.'
    $taskChaveDigitada = Read-Host 'Chave da Groq' -AsSecureString
    Save-ConexoChaveIA -PastaProjeto $taskPastaProjeto -Chave $taskChaveDigitada
    Enable-ConexoIA -PastaProjeto $taskPastaProjeto
    Write-Host 'Pronto! A chave foi salva e a IA foi habilitada.' -ForegroundColor Green
    Write-Host 'Encerre o servidor anterior, se estiver aberto. Depois abra Servidor.cmd normalmente pelo Explorador.'
    Write-Host 'Nas proximas vezes, a chave sera carregada automaticamente nesta conta do Windows.'
    Write-Host 'A primeira pergunta no chat confirma se a Groq aceita a chave.'
    exit 0
} catch {
    Write-Host 'Nao foi possivel concluir. Confira se colou a chave completa e se a pasta permite salvar arquivos.' -ForegroundColor Red
    Write-Host 'Voce pode executar ConfigurarIA.cmd novamente.'
    exit 1
} finally {
    if ($null -ne $taskChaveDigitada) { $taskChaveDigitada.Dispose() }
}

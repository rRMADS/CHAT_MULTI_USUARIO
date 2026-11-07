# Credencial local protegida pelo Windows. Nenhuma funcao imprime a chave.
function Get-ConexoArquivoChave {
    param([Parameter(Mandatory=$true)][string]$PastaProjeto)
    return Join-Path $PastaProjeto '.local\ia\groq-key.dpapi'
}

function Set-ConexoTextoAtomico {
    param([string]$Arquivo, [string]$Texto)
    $taskPastaDestino = Split-Path -Parent $Arquivo
    [System.IO.Directory]::CreateDirectory($taskPastaDestino) | Out-Null
    $taskTemporario = $Arquivo + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [System.IO.File]::WriteAllText($taskTemporario, $Texto, (New-Object System.Text.UTF8Encoding($false)))
        Move-Item -LiteralPath $taskTemporario -Destination $Arquivo -Force
    } finally {
        if (Test-Path -LiteralPath $taskTemporario) { Remove-Item -LiteralPath $taskTemporario -Force }
    }
}

function Save-ConexoChaveIA {
    param([string]$PastaProjeto, [System.Security.SecureString]$Chave)
    if ($null -eq $Chave -or $Chave.Length -eq 0 -or $Chave.Length -gt 1024) {
        throw 'Cole a chave completa gerada pela Groq.'
    }
    $taskTextoChave = $null
    $taskChaveNormalizada = $null
    try {
        $taskCredencial = New-Object System.Management.Automation.PSCredential('groq', $Chave)
        $taskTextoChave = $taskCredencial.GetNetworkCredential().Password.Trim()
        if ($taskTextoChave -notmatch '^[\x21-\x7E]{16,1024}$') { throw 'Cole a chave completa da Groq, sem espacos ou quebras de linha.' }
        $taskChaveNormalizada = ConvertTo-SecureString -String $taskTextoChave -AsPlainText -Force
        # Sem -Key: no Windows, ConvertFrom-SecureString usa a protecao da conta (DPAPI).
        $taskProtegida = ConvertFrom-SecureString -SecureString $taskChaveNormalizada
        Set-ConexoTextoAtomico -Arquivo (Get-ConexoArquivoChave $PastaProjeto) -Texto $taskProtegida
    } finally {
        $taskTextoChave = $null
        $taskCredencial = $null
        if ($null -ne $taskChaveNormalizada) { $taskChaveNormalizada.Dispose() }
    }
}

function Import-ConexoChaveIA {
    param([string]$PastaProjeto)
    if (![string]::IsNullOrWhiteSpace($env:GROQ_API_KEY)) { return $true }
    $taskArquivoChave = Get-ConexoArquivoChave $PastaProjeto
    if (!(Test-Path -LiteralPath $taskArquivoChave)) { return $false }
    $taskSegredo = $null
    try {
        $taskCifrada = [System.IO.File]::ReadAllText($taskArquivoChave)
        if ($taskCifrada.Length -gt 16384) { throw 'Arquivo invalido' }
        $taskSegredo = ConvertTo-SecureString -String $taskCifrada -ErrorAction Stop
        $taskCredencial = New-Object System.Management.Automation.PSCredential('groq', $taskSegredo)
        $env:GROQ_API_KEY = $taskCredencial.GetNetworkCredential().Password
        return ![string]::IsNullOrWhiteSpace($env:GROQ_API_KEY)
    } catch {
        throw 'Nao foi possivel ler a chave salva nesta conta do Windows. Execute ConfigurarIA.cmd para configura-la novamente.'
    } finally {
        if ($null -ne $taskSegredo) { $taskSegredo.Dispose() }
        $taskCredencial = $null
    }
}

function Enable-ConexoIA {
    param([string]$PastaProjeto)
    $taskArquivoConfig = Join-Path $PastaProjeto 'chat.properties'
    $taskExemploConfig = Join-Path $PastaProjeto 'chat.properties.example'
    $taskConteudoConfig = ''
    if (Test-Path -LiteralPath $taskArquivoConfig) { $taskConteudoConfig = [System.IO.File]::ReadAllText($taskArquivoConfig) }
    elseif (Test-Path -LiteralPath $taskExemploConfig) { $taskConteudoConfig = [System.IO.File]::ReadAllText($taskExemploConfig) }
    $taskPadraoIA = '(?m)^[\t ]*ai\.enabled(?:[\t ]*[:=][\t ]*|[\t ]+)[^\r\n]*'
    if ([regex]::IsMatch($taskConteudoConfig, $taskPadraoIA)) {
        $taskConteudoConfig = [regex]::Replace($taskConteudoConfig, $taskPadraoIA, 'ai.enabled=true')
    } else { $taskConteudoConfig = $taskConteudoConfig.TrimEnd() + "`r`nai.enabled=true`r`n" }
    if ($taskConteudoConfig -notmatch '(?m)^[\t ]*ai\.model(?:[\t ]*[:=]|[\t ]+)') {
        $taskConteudoConfig = $taskConteudoConfig.TrimEnd() + "`r`nai.model=openai/gpt-oss-20b`r`n"
    }
    Set-ConexoTextoAtomico -Arquivo $taskArquivoConfig -Texto $taskConteudoConfig
}

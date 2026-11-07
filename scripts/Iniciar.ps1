param(
    [ValidateSet('Servidor', 'Cliente', 'Testes', 'Preparar')]
    [string]$Modo = 'Cliente',
    [switch]$Recompilar
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $taskRoot

try {
    $taskJdk = $env:JAVA_HOME
    if (!$taskJdk -or !(Test-Path -LiteralPath (Join-Path $taskJdk 'bin\javac.exe'))) {
        $taskPastasJdk = @(
            "$env:ProgramFiles\Java\jdk*",
            "$env:ProgramFiles\Eclipse Adoptium\jdk*",
            "$env:ProgramFiles\Microsoft\jdk*",
            "$env:ProgramFiles\Amazon Corretto\jdk*",
            "$env:ProgramFiles\Zulu\zulu*"
        )
        $taskJdk = Get-ChildItem -Path $taskPastasJdk -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (!$taskJdk) {
        $taskJavac = Get-Command javac.exe -ErrorAction SilentlyContinue
        if ($taskJavac) { $taskJdk = Split-Path -Parent (Split-Path -Parent $taskJavac.Source) }
    }
    if (!$taskJdk) { throw 'Instale um JDK 17 ou superior (recomendado: JDK 21) e configure JAVA_HOME.' }
    $env:JAVA_HOME = $taskJdk
    $taskJava = Join-Path $taskJdk 'bin\java.exe'
    $taskVersao = (& (Join-Path $taskJdk 'bin\javac.exe') -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $taskVersao -notmatch 'javac\s+(\d+)' -or [int]$Matches[1] -lt 17) {
        throw 'E necessário um JDK 17 ou superior. Configure JAVA_HOME para a pasta do JDK (recomendado: JDK 21).'
    }
    $taskMaven = Join-Path $taskRoot 'mvnw.cmd'
    if (!(Test-Path -LiteralPath $taskMaven)) { throw 'Extraia o projeto completo, incluindo mvnw.cmd e a pasta .mvn.' }
    $env:MAVEN_USER_HOME = Join-Path $taskRoot '.m2'
    $taskMarker = Join-Path $taskRoot 'target\.preparado'
    $taskFontes = @(Get-ChildItem -LiteralPath 'src' -File -Recurse) + @(Get-Item -LiteralPath 'pom.xml')
    $taskPrecisaCompilar = $Recompilar -or !(Test-Path -LiteralPath $taskMarker)
    if (!$taskPrecisaCompilar) {
        $taskData = (Get-Item -LiteralPath $taskMarker).LastWriteTime
        $taskPrecisaCompilar = @($taskFontes | Where-Object LastWriteTime -gt $taskData).Count -gt 0
    }
    if ($taskPrecisaCompilar -or $Modo -eq 'Testes') {
        $taskArgumentos = @('-B', '-ntp', "-Dmaven.repo.local=$taskRoot\.m2")
        if ($Modo -eq 'Testes') { & $taskMaven @taskArgumentos test }
        else { & $taskMaven @taskArgumentos '-DskipTests' compile dependency:copy-dependencies '-DincludeScope=runtime' }
        if ($LASTEXITCODE -ne 0) { throw 'Não foi possível preparar o projeto. Confira as mensagens acima e a conexão com a internet.' }
        if ($Modo -eq 'Testes') { Write-Host 'Todos os testes passaram.' -ForegroundColor Green; exit 0 }
        New-Item -ItemType File -Path $taskMarker -Force | Out-Null
    }
    if ($Modo -eq 'Preparar') { Write-Host 'Projeto pronto para executar.' -ForegroundColor Green; exit 0 }
    $taskClasspath = (Join-Path $taskRoot 'target\classes') + ';' + (Join-Path $taskRoot 'target\dependency\*')
    if ($Modo -eq 'Servidor') {
        . (Join-Path $PSScriptRoot 'ChaveIA.ps1')
        try {
            $taskIAComChave = Import-ConexoChaveIA -PastaProjeto $taskRoot
            if (!$taskIAComChave) { Write-Host 'Para lembrar a chave da IA neste computador, abra ConfigurarIA.cmd uma vez.' -ForegroundColor Yellow }
        } catch {
            Write-Host 'A chave salva nao pode ser lida nesta conta do Windows. Abra ConfigurarIA.cmd para configura-la novamente.' -ForegroundColor Yellow
        }
        Write-Host 'Iniciando servidor. Mantenha esta janela aberta durante a conversa.' -ForegroundColor Green
        & $taskJava -cp $taskClasspath server.Servidor
    } else {
        $taskFx = (Get-ChildItem -Path (Join-Path $taskRoot 'target\dependency\javafx-*-win.jar') | ForEach-Object FullName) -join ';'
        if (!$taskFx) { throw 'Bibliotecas gráficas não encontradas. Execute novamente com -Recompilar.' }
        & $taskJava --module-path $taskFx --add-modules javafx.controls --enable-native-access=javafx.graphics -cp $taskClasspath client.Launcher
    }
    if ($LASTEXITCODE -ne 0) { throw 'A execução terminou com erro. Confira as mensagens acima.' }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

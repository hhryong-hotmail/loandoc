<#
Safe helper script to remove skeeperweb artifacts from a local Tomcat installation.

USAGE:
  # Dry-run (default): shows what would be removed
  .\remove_skeeperweb.ps1

  # Actually perform removal
  .\remove_skeeperweb.ps1 -ConfirmRemoval

NOTES:
- This script assumes Tomcat home at D:\apache-tomcat-9.0.98 by default. Change $TomcatHome if needed.
- Stop Tomcat before running if you prefer; the script will try to call shutdown.bat if available.
- You must run PowerShell as an Administrator to remove files under Program Files.
- The script will NOT attempt to delete anything outside the configured Tomcat home.
#>

param(
    [switch]$ConfirmRemoval
)

$TomcatHome = 'D:\apache-tomcat-9.0.98'
$Webapps = Join-Path $TomcatHome 'webapps'
$Work = Join-Path $TomcatHome 'work'
$Temp = Join-Path $TomcatHome 'temp'

# Targets
$warFile = Join-Path $Webapps 'skeeperweb.war'
$appDir = Join-Path $Webapps 'skeeperweb'
$workApp = Join-Path $Work 'Catalina' ; $workApp = Join-Path $workApp 'localhost' ; $workApp = Join-Path $workApp 'skeeperweb'
$workPaths = @($workApp, Join-Path $Work 'skeeperweb')

Write-Output "Tomcat home: $TomcatHome"
Write-Output "webapps: $Webapps"
Write-Output "Targets:"
Write-Output "  WAR: $warFile"
Write-Output "  APP DIR: $appDir"
Write-Output "  WORK PATHS: $($workPaths -join ', ')"

if(-not (Test-Path $TomcatHome)){
    Write-Error "Tomcat 홈을 찾을 수 없습니다: $TomcatHome. 경로가 다르면 스크립트 상단의 변수 수정하세요." ; exit 1
}

# Try graceful shutdown if shutdown.bat exists
$shutdown = Join-Path $TomcatHome 'bin\shutdown.bat'
if(Test-Path $shutdown){
    Write-Output "shutdown.bat 발견: Tomcat을 정상적으로 종료 시도합니다..."
    & $shutdown
    Start-Sleep -Seconds 3
    # wait up to 20s for Java processes to stop
    $wait = 0
    while((Get-Process -Name java -ErrorAction SilentlyContinue) -and ($wait -lt 20)){
        Start-Sleep -Seconds 1
        $wait++
    }
    if((Get-Process -Name java -ErrorAction SilentlyContinue)){
        Write-Warning "Java 프로세스가 여전히 실행 중일 수 있습니다. 강제 삭제를 원하면 관리자 권한으로 실행하고 ConfirmRemoval 스위치를 사용하세요."
    } else { Write-Output "Tomcat 프로세스 종료 완료(또는 없었음)." }
} else {
    Write-Output "shutdown.bat를 찾지 못했습니다. Tomcat이 서비스로 실행중이면 서비스 중지를 권장합니다." 
}

# Deletion operations (dry-run unless ConfirmRemoval)
function Do-Remove([string]$path){
    if(Test-Path $path){
        if($ConfirmRemoval){
            try{
                Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction Stop
                Write-Output "삭제됨: $path"
            } catch {
                Write-Warning "삭제 실패: $path  - $_"
            }
        } else {
            Write-Output "(dry-run) 제거 예정: $path"
        }
    } else {
        Write-Output "존재하지 않음: $path"
    }
}

# Remove war and app dir
Do-Remove $warFile
Do-Remove $appDir

# Remove work directories
foreach($p in $workPaths){ Do-Remove $p }

# Clear temp/catalina temp folders that may reference the webapp
Do-Remove (Join-Path $Temp 'catalina*')

if($ConfirmRemoval){
    # Optionally start Tomcat again
    $startup = Join-Path $TomcatHome 'bin\startup.bat'
    if(Test-Path $startup){
        Write-Output "Tomcat을 다시 시작합니다..."
        & $startup
    } else { Write-Output "startup.bat를 찾을 수 없습니다. Tomcat을 수동으로 시작하세요." }
} else {
    Write-Output "주의: 현 상태는 dry-run입니다. 실제 삭제를 실행하려면 -ConfirmRemoval 스위치와 함께 다시 실행하세요. 예: .\remove_skeeperweb.ps1 -ConfirmRemoval"
}

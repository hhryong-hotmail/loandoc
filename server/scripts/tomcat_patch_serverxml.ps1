<#
PowerShell script to safely patch Tomcat's conf/server.xml.
Operations:
 - create timestamped backup of server.xml
 - add address="127.0.0.1" to the <Server ...> element (mode: add-address)
 - or disable shutdown port by setting port="-1" on the <Server ...> element (mode: disable-port)

Usage examples:
  # add address attribute
  .\tomcat_patch_serverxml.ps1 -TomcatHome 'D:\apache-tomcat-9.0.98' -Mode add-address

  # disable shutdown port
  .\tomcat_patch_serverxml.ps1 -TomcatHome 'D:\apache-tomcat-9.0.98' -Mode disable-port
#>
[CmdletBinding()]
param(
    [string]$TomcatHome = 'D:\apache-tomcat-9.0.98',
    [ValidateSet('add-address','disable-port')]
    [string]$Mode = 'add-address',
    [string]$Address = '127.0.0.1'
)

$serverXml = Join-Path -Path $TomcatHome -ChildPath 'conf\server.xml'
if (-not (Test-Path $serverXml)){
    Write-Error "server.xml not found at $serverXml"
    exit 1
}

# backup
$timestamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
$backup = "$serverXml.bak.$timestamp"
Copy-Item -Path $serverXml -Destination $backup -Force
Write-Host "Backup created: $backup"

# load XML
try{
    [xml]$xml = Get-Content -Path $serverXml -Raw
}catch{
    Write-Error "Failed to read server.xml: $_"
    exit 1
}

$serverNode = $xml.SelectSingleNode('/Server')
if(-not $serverNode){
    # try without leading slash
    $serverNode = $xml.SelectSingleNode('Server')
}
if(-not $serverNode){
    Write-Error "Could not locate <Server> element in server.xml"
    exit 1
}

if($Mode -eq 'add-address'){
    if($serverNode.hasAttribute('address')){
        $old = $serverNode.getAttribute('address')
        if($old -eq $Address){
            Write-Host "address attribute already set to $Address. No change made."
        } else {
            Write-Host "Replacing existing address attribute value '$old' -> '$Address'"
            $serverNode.setAttribute('address', $Address)
            $xml.Save($serverXml)
            Write-Host "Updated server.xml: address='$Address'"
        }
    } else {
        Write-Host "Adding address='$Address' to <Server>"
        $serverNode.setAttribute('address', $Address)
        $xml.Save($serverXml)
        Write-Host "Updated server.xml: address='$Address'"
    }
} else {
    # disable shutdown port
    if($serverNode.hasAttribute('port')){
        $old = $serverNode.getAttribute('port')
        if($old -eq '-1'){
            Write-Host "shutdown port already set to -1 (disabled). No change made."
        } else {
            Write-Host "Changing port attribute value '$old' -> '-1' (disable shutdown port)"
            $serverNode.setAttribute('port','-1')
            $xml.Save($serverXml)
            Write-Host "Updated server.xml: port='-1'"
        }
    } else {
        Write-Host "No explicit port attribute found on <Server>, adding port='-1' to disable shutdown port"
        $serverNode.setAttribute('port','-1')
        $xml.Save($serverXml)
        Write-Host "Updated server.xml: port='-1'"
    }
}

Write-Host "Done. Review $serverXml and the backup at $backup if needed."
$ErrorActionPreference = 'Stop'

$base = 'http://127.0.0.1:8080'

function PostJson([string]$url, $obj, $hdr) {
  $body = $obj | ConvertTo-Json -Depth 6
  Invoke-RestMethod -Uri $url -Method Post -ContentType 'application/json' -Body $body -Headers $hdr
}

function GetJson([string]$url, $hdr) {
  Invoke-RestMethod -Uri $url -Method Get -Headers $hdr
}

function TryRegister([string]$u, [string]$p) {
  try { PostJson ($base + '/api/user/register') @{ username = $u; password = $p } @{} | Out-Null } catch {}
}

function Login([string]$u, [string]$p) {
  (PostJson ($base + '/api/user/login') @{ username = $u; password = $p } @{}).data.token
}

function Me([hashtable]$h) {
  (GetJson ($base + '/api/user/me') $h).data
}

$pw = 'Pass@123456'
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$A = 'qaA_' + $ts
$B = 'qaB_' + $ts
$C = 'qaC_' + $ts
$D = 'qaD_' + $ts

TryRegister $A $pw
TryRegister $B $pw
TryRegister $C $pw
TryRegister $D $pw

$tA = Login $A $pw
$tB = Login $B $pw
$tC = Login $C $pw
$tD = Login $D $pw

$hA = @{ Authorization = ('Bearer ' + $tA) }
$hB = @{ Authorization = ('Bearer ' + $tB) }
$hC = @{ Authorization = ('Bearer ' + $tC) }
$hD = @{ Authorization = ('Bearer ' + $tD) }

# A create room
$rCreate = PostJson ($base + '/api/chat/room/create') @{ name = '验收群聊'; memberUserIds = @() } $hA
$roomId = $rCreate.data.roomId
$chatNo = $rCreate.data.chatNo
Write-Output ('roomId=' + $roomId + ' chatNo=' + $chatNo)

# B search and apply
$rSearch = GetJson ($base + '/api/chat/search?chatNo=' + $chatNo) $hB
Write-Output ('search isMember=' + $rSearch.data.isMember + ' memberCount=' + $rSearch.data.memberCount)
$rApply = PostJson ($base + '/api/chat/room/' + $roomId + '/apply') @{ reason = '想加入测试' } $hB
$applyId = $rApply.data.applyId
Write-Output ('applyId=' + $applyId + ' status=' + $rApply.data.status)

# A approve
$rApplies = GetJson ($base + '/api/chat/room/' + $roomId + '/applies?status=pending') $hA
Write-Output ('pendingApplies=' + $rApplies.data.Count)
PostJson ($base + '/api/chat/apply/' + $applyId + '/review?action=approved') @{} $hA | Out-Null
Write-Output 'apply approved'

# A set C admin
$cId = (Me $hC).id
PostJson ($base + '/api/chat/room/' + $roomId + '/members/' + $cId + '/role') @{ role = 'admin' } $hA | Out-Null
Write-Output 'C set admin'

# C invite D
$dId = (Me $hD).id
$rInv = PostJson ($base + '/api/chat/room/' + $roomId + '/invite') @{ inviteeUserId = $dId } $hC
$inviteId = $rInv.data.inviteId
Write-Output ('inviteId=' + $inviteId)

# D accept invite
$rMyInv = GetJson ($base + '/api/chat/invites/my?status=pending') $hD
Write-Output ('myInvites=' + $rMyInv.data.Count)
PostJson ($base + '/api/chat/invite/' + $inviteId + '/respond?action=accepted') @{} $hD | Out-Null
Write-Output 'invite accepted'

# D leave
PostJson ($base + '/api/chat/room/' + $roomId + '/leave') @{} $hD | Out-Null
Write-Output 'D left'

# A close
PostJson ($base + '/api/chat/room/' + $roomId + '/close') @{} $hA | Out-Null
Write-Output 'A closed'

# Check notifications
$rNB = GetJson ($base + '/api/message/list') $hB
$rND = GetJson ($base + '/api/message/list') $hD
$typesB = ($rNB.data | Select-Object -First 5 | ForEach-Object { $_.type }) -join ','
$typesD = ($rND.data | Select-Object -First 5 | ForEach-Object { $_.type }) -join ','
Write-Output ('B notices types(top5)=' + $typesB)
Write-Output ('D notices types(top5)=' + $typesD)
Write-Output 'done'


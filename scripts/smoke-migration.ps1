$ErrorActionPreference = 'Stop'

function PostJson([string]$url, [string]$json, [string]$token = '') {
  $headers = @{}
  if ($token) { $headers.Authorization = 'Bearer ' + $token }
  return Invoke-RestMethod -Method Post -Uri $url -ContentType 'application/json' -Headers $headers -Body $json
}

function GetJson([string]$url, [string]$token = '') {
  $headers = @{}
  if ($token) { $headers.Authorization = 'Bearer ' + $token }
  return Invoke-RestMethod -Method Get -Uri $url -Headers $headers
}

function TryPostJson([string]$url, [string]$json, [string]$token = '') {
  try { return PostJson $url $json $token } catch { return $null }
}

function LoginWithRetry([string]$username, [string]$password) {
  $attempt = 0
  while ($attempt -lt 4) {
    $attempt++
    try {
      $payload = @{ username = $username; password = $password } | ConvertTo-Json
      return PostJson 'http://localhost:8080/api/user/login' $payload
    } catch {
      Start-Sleep -Seconds (2 * $attempt)
      if ($attempt -ge 4) { throw }
    }
  }
}

$ownerUser = 'smoke_owner'
$devUser = 'smoke_dev'
$ownerPhone = '13800001001'
$devPhone = '13800001002'
$pwd = 'abc123'

# 注册（已存在则忽略）
TryPostJson 'http://localhost:8080/api/user/register' ((@{ username = $ownerUser; phone = $ownerPhone; password = $pwd }) | ConvertTo-Json) | Out-Null
TryPostJson 'http://localhost:8080/api/user/register' ((@{ username = $devUser; phone = $devPhone; password = $pwd }) | ConvertTo-Json) | Out-Null

$login1 = LoginWithRetry $ownerUser $pwd
Start-Sleep -Seconds 2
$login2 = LoginWithRetry $devUser $pwd

$t1 = $login1.data.token
$t2 = $login2.data.token
if (-not $t1 -or -not $t2) { throw 'login failed: token missing' }

# 发布协作（旧模型 /api/collab/*）
$pubResp = PostJson 'http://localhost:8080/api/collab/publish' ((@{ title = '联调协作任务'; content = '用于迁移验收：发布/申请/审核/通知'; minCredit = 0 }) | ConvertTo-Json) $t1
Write-Output ('collab.publish.resp: ' + ($pubResp | ConvertTo-Json -Depth 6))

$myPub = GetJson 'http://localhost:8080/api/collab/myPublish' $t1
Write-Output ('collab.myPublish.raw: ' + ($myPub | ConvertTo-Json -Depth 6))

$hall = GetJson 'http://localhost:8080/api/collab/list' $t1
if (-not $hall -or -not $hall.data) {
  Write-Output ('collab.list.empty: ' + ($hall | ConvertTo-Json -Depth 6))
  throw 'no data from /api/collab/list'
}
Write-Output ('collab.list.raw: ' + ($hall | ConvertTo-Json -Depth 6))
$projectId = ($myPub.data | Select-Object -First 1).id
if (-not $projectId) {
  # 兜底从大厅取第一条
  $projectId = ($hall.data | Select-Object -First 1).id
}
if (-not $projectId) { throw 'no projectId from /api/collab/list' }

# 申请
Invoke-RestMethod -Method Post -Uri ("http://localhost:8080/api/collab/apply?projectId=$projectId") -Headers @{ Authorization = 'Bearer ' + $t2 } | Out-Null

# 审核：找到 pending 的申请并通过
$alist = Invoke-RestMethod -Method Get -Uri ("http://localhost:8080/api/collab/applyList?projectId=$projectId") -Headers @{ Authorization = 'Bearer ' + $t1 }
$pending = ($alist.data | Where-Object { $_.status -eq 'pending' } | Select-Object -First 1)
if ($pending) {
  PostJson 'http://localhost:8080/api/collab/apply/review' (@{ applyId = $pending.applyId; action = 'approve'; reason = 'ok' } | ConvertTo-Json) $t1 | Out-Null
}

# 拉消息列表验证（至少应有协作申请、审核结果等通知）
$msgOwner = GetJson 'http://localhost:8080/api/message/list' $t1
$msgDev = GetJson 'http://localhost:8080/api/message/list' $t2

[pscustomobject]@{
  owner = $login1.data.user.username
  dev = $login2.data.user.username
  projectId = $projectId
  ownerMsgCount = ($msgOwner.data | Measure-Object).Count
  devMsgCount = ($msgDev.data | Measure-Object).Count
  ownerLatestTitle = ($msgOwner.data | Select-Object -First 1).title
  devLatestTitle = ($msgDev.data | Select-Object -First 1).title
} | ConvertTo-Json -Depth 5


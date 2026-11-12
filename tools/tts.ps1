# 앱이 실제로 읽은 안내 문장을 Windows 한국어 음성(Heami)으로 WAV로 만든다 (영상 소리용)
# 사용: powershell -File tts.ps1 <lines.json> <출력 폴더>   lines.json = [{"id":"t0","text":"..."}, ...]
param([string]$Json, [string]$Out)
Add-Type -AssemblyName System.Speech
New-Item -ItemType Directory -Force $Out | Out-Null
$items = Get-Content $Json -Raw -Encoding UTF8 | ConvertFrom-Json
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice('Microsoft Heami Desktop')
$s.Rate = 1
foreach ($it in $items) {
  $path = Join-Path $Out ($it.id + '.wav')
  $s.SetOutputToWaveFile($path)
  $s.Speak($it.text)
  $s.SetOutputToNull()
  $it.id
}
$s.Dispose()

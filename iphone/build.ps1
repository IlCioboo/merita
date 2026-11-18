# Lipește core.js + merita-main.js într-un singur fișier pentru Scriptable: iphone\Merita.js
$here = $PSScriptRoot
$header = @'
// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: green; icon-glyph: car;

// Merita - calculator profit curse Glovo / Wolt / Uber Eats (fișier generat de build.ps1)

'@
$core = Get-Content (Join-Path $here '..\core.js') -Raw -Encoding UTF8
$main = Get-Content (Join-Path $here 'merita-main.js') -Raw -Encoding UTF8
$out = Join-Path $here 'Merita.js'
[IO.File]::WriteAllText($out, $header + $core + "`n" + $main, (New-Object Text.UTF8Encoding $false))
Write-Host "Scris: $out"

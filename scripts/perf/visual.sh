#!/usr/bin/env bash
# Captura visual do Termclado no emulador com a geometria do celular do usuário
# (720x1520 px, 288 dpi = 1,8 px/dp), digita nas teclas de verdade e guarda as imagens.
# Uso: scripts/perf/visual.sh <pasta_de_saida> <apk_debug>
set -uo pipefail

OUT="${1:-visual-results}"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.aistudio.tecladoptbr.kxqpva"
IME="$PKG/com.example.keyboard.LatinIMEKeyboardService"
mkdir -p "$OUT"
REL="$OUT/relatorio.txt"
: > "$REL"

log() { echo "$*" | tee -a "$REL"; echo "::notice::$*"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || log "falha ao capturar $1"; }
resumed() { adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -E "topResumedActivity|mResumedActivity" | head -1 | grep -o "$PKG/[A-Za-z0-9_.]*" | head -1; }
geo() { python3 scripts/perf/analisar_teclado.py "$1" --json > "$OUT/geo.json" 2>/dev/null; }
topo() { python3 -c "import json; print(json.load(open('$OUT/geo.json')).get('teclado_topo', 0))"; }
tecla() {  # linha (1-5) e índice (0...) -> "x y" do centro da tecla
  python3 - "$OUT/geo.json" "$1" "$2" <<'PYEOF'
import json, sys
g = json.load(open(sys.argv[1])); r = int(sys.argv[2]) - 1; i = int(sys.argv[3])
t = g["teclas_por_linha"][r][i]
print(t["x0"] + t["largura"] // 2, g["teclado_topo"] + t["topo"] + t["altura"] // 2)
PYEOF
}
tocar() { local xy; xy=$(tecla "$1" "$2"); adb shell input tap $xy; sleep 0.35; }

restaurar() { adb shell wm size reset >/dev/null 2>&1; adb shell wm density reset >/dev/null 2>&1; }
trap restaurar EXIT

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
adb shell input keyevent 82 >/dev/null 2>&1 || true

log "Tela original: $(adb shell wm size | tr -d '\r' | tail -1) | $(adb shell wm density | tr -d '\r' | tail -1)"
adb shell wm size 720x1520 >/dev/null
adb shell wm density 288 >/dev/null
sleep 6
log "Tela usada:    $(adb shell wm size | tr -d '\r' | tail -1) | $(adb shell wm density | tr -d '\r' | tail -1)"

log "Instalação: $(adb install -r "$APK" 2>&1 | tr -d '\r' | tail -1)"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell settings put secure show_ime_with_hard_keyboard 1 >/dev/null 2>&1 || true
for t in 1 2 3 4 5 6 7 8 9 10; do
  out=$(adb shell ime enable "$IME" 2>&1 | tr -d '\r')
  echo "$out" | grep -q "Unknown input method" || break
  sleep 3
done
adb shell ime set "$IME" >/dev/null 2>&1 || true
log "Teclado ativo: $(adb shell dumpsys input_method 2>/dev/null | tr -d '\r' | grep -o 'mCurMethodId=[^ ]*' | head -1)"

# --- Tela inicial do app (lista de configurações) ---
adb shell am start -W -n "$PKG/com.example.MainActivity" >/dev/null 2>&1 || true
sleep 5
shot 01-app-inicio
adb shell input swipe 360 1300 360 500 400; sleep 1.5
shot 02-app-rolado
adb shell input swipe 360 1300 360 500 400; sleep 1.5
shot 02b-app-rolado-mais
adb shell input keyevent KEYCODE_BACK; sleep 1

# --- Teclado: abre o campo de teste e espera o serviço do IME ---
for t in 1 2 3 4 5 6; do
  adb shell am start -W -n "$PKG/com.example.DevTypingActivity" >/dev/null 2>&1 || true
  sleep 7
  adb shell dumpsys input_method 2>/dev/null | tr -d '\r' | grep -q "mInputShown=true" && break
  log "Teclado ainda não visível (tentativa $t)"
done
log "Teclado visível: $(adb shell dumpsys input_method 2>/dev/null | tr -d '\r' | grep -o 'mInputShown=[a-z]*' | head -1)"
shot 03-teclado-sugestoes
geo "$OUT/03-teclado-sugestoes.png"
TOPO=$(topo)
Y=$((TOPO + 38))
log "Topo do teclado: $TOPO px; linha dos ícones da barra: y=$Y"

# --- Linha de ferramentas: toca na grade (esquerda) ---
adb shell input tap 41 "$Y"; sleep 2
shot 04-teclado-ferramentas
adb shell input tap 41 "$Y"; sleep 1.5   # fecha

# --- Teste do toque indevido: toque numa sugestão e logo depois onde ficavam os ícones ---
adb shell input tap 520 "$Y"; sleep 0.3
adb shell input tap 550 "$Y"; sleep 1.5
ATUAL=$(resumed)
if echo "$ATUAL" | grep -q "DevTypingActivity"; then
  log "TESTE toque indevido na barra: OK (continua em $ATUAL)"
else
  log "TESTE toque indevido na barra: FALHOU (foi para ${ATUAL:-desconhecido})"
fi
adb shell am start -W -n "$PKG/com.example.DevTypingActivity" >/dev/null 2>&1 || true
sleep 3

# --- Digitação real nas teclas: "teclado" (linha 2: t=4 e=2 o=8; linha 3: a=0 d=2 l=8; linha 4: c=3) ---
geo "$OUT/03-teclado-sugestoes.png"
tocar 2 4; tocar 2 2; tocar 4 3; tocar 3 8; tocar 3 0; tocar 3 2; tocar 2 8
sleep 1.5
shot 05-digitando-teclado

# Enter: a barra não deve repetir a palavra
ENTER_XY=$(python3 -c "
import json; g=json.load(open('$OUT/geo.json')); t=g['teclas_por_linha'][4][-1]
print(t['x0']+t['largura']//2, g['teclado_topo']+t['topo']+t['altura']//2)")
adb shell input tap $ENTER_XY; sleep 1.5
shot 06-apos-enter

# --- Pílula de captura de tela ---
python3 scripts/perf/make_png.py "$OUT/Screenshot_teste.png" 2>/dev/null
adb shell mkdir -p /sdcard/Pictures/Screenshots >/dev/null 2>&1 || true
adb push "$OUT/Screenshot_teste.png" /sdcard/Pictures/Screenshots/Screenshot_teste.png >/dev/null 2>&1 || true
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/Screenshots/Screenshot_teste.png >/dev/null 2>&1 || true
adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_BACK; sleep 1.5   # esconde o teclado para a barra ler a captura ao reabrir
adb shell am start -W -n "$PKG/com.example.DevTypingActivity" >/dev/null 2>&1 || true
sleep 8
adb shell ime set "$IME" >/dev/null 2>&1 || true
sleep 2
shot 07-teclado-pilula

log "Concluído. Imagens em $OUT/"
exit 0

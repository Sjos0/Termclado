#!/usr/bin/env bash
# Mede RAM e armazenamento do Termclado em um emulador Android (execução no GitHub Actions).
# Uso: scripts/perf/medir.sh <pasta_de_saida> <caminho_do_apk_debug>
# Cada passo vira uma anotação (::notice::) para aparecer no GitHub sem baixar o log.
set -uo pipefail

OUT="${1:-perf-results}"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.aistudio.tecladoptbr.kxqpva"
IME="$PKG/com.example.keyboard.LatinIMEKeyboardService"
mkdir -p "$OUT"
RESUMO="$OUT/resumo.txt"
: > "$RESUMO"

log() { echo "$*" | tee -a "$RESUMO"; echo "::notice::$*"; }
trap 'echo "::error::medir.sh parou na linha $LINENO (código $?)"' ERR

log "Motor: Rust (libkeyboard_core.so)"
log "Android: $(adb shell getprop ro.build.version.release | tr -d '\r') (SDK $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
log "Modelo emulado: $(adb shell getprop ro.product.model | tr -d '\r')"
log "APK: $APK ($(stat -c %s "$APK" 2>/dev/null || echo ?) bytes no runner)"

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
adb shell input keyevent 82 >/dev/null 2>&1 || true

log "Instalação:"
inst=$(adb install -r "$APK" 2>&1 | tr -d '\r')
log "  $inst"

log "Lista de IMEs instalados (diagnóstico):"
adb shell ime list -a -s 2>&1 | tr -d '\r' | head -5 | while read -r l; do log "  $l"; done

log "Benchmark do motor (processo isolado, sem IME):"
adb logcat -c 2>/dev/null || true
adb shell am start -W -n "$PKG/com.example.DevTypingActivity" --ez bench true >/dev/null 2>&1 || true
for i in $(seq 1 30); do
  if adb logcat -d -s EngineBench:I 2>/dev/null | grep -q "EngineBench.*|FIM"; then break; fi
  sleep 3
done
adb logcat -d -s EngineBench:I 2>/dev/null | tr -d '\r' | sed -n 's/.*EngineBench: //p' > "$OUT/bench.txt"
grep "^MEM|" "$OUT/bench.txt" | while read -r l; do log "  $l"; done
log "  linhas de equivalência: AC=$(grep -c '^AC|' "$OUT/bench.txt") SG=$(grep -c '^SG|' "$OUT/bench.txt")"
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
sleep 3

log "Ativando o Termclado como teclado (com novas tentativas):"
ativo=0
for tentativa in 1 2 3 4 5 6 7 8 9 10; do
  saida=$(adb shell ime enable "$IME" 2>&1 | tr -d '\r' | tr '\n' ' ')
  if ! echo "$saida" | grep -q "Unknown input method"; then ativo=1; break; fi
  sleep 4
done
log "  enable após $tentativa tentativa(s): $([ "$ativo" = 1 ] && echo ok || echo 'FALHOU')"
log "  set: $(adb shell ime set "$IME" 2>&1 | tr -d '\r' | tr '\n' ' ')"

log "Forçando o teclado virtual a aparecer (sem teclado físico):"
adb shell settings put secure show_ime_with_hard_keyboard 1 >/dev/null 2>&1 || true

log "Abrindo o campo de teste:"
log "  $(adb shell am start -W -n "$PKG/com.example.DevTypingActivity" 2>&1 | tr -d '\r' | tr '\n' ' ')"
sleep 8

snapshot() {
  local nome="$1"
  adb shell dumpsys meminfo "$PKG" 2>/dev/null | tr -d '\r' > "$OUT/$nome-meminfo.txt"
  local pss
  pss=$(grep -E "TOTAL PSS" "$OUT/$nome-meminfo.txt" | head -1 | grep -oE "[0-9]+" | head -1)
  log "RAM ($nome): PSS total = ${pss:-indisponível} KB"
  grep -E "Java Heap|Native Heap|Graphics|Private Dirty|TOTAL" "$OUT/$nome-meminfo.txt" | head -8 | while read -r l; do log "  $l"; done
}
log "Serviço do IME em execução:"
adb shell dumpsys activity services "$PKG" 2>/dev/null | tr -d '\r' | grep -E "LatinIMEKeyboardService|ServiceRecord" | head -4 | while read -r l; do log "  $l"; done
# A medição vale quando o SERVIÇO do IME está rodando (o dicionário é carregado na criação do serviço).
# A exibição do teclado é registrada à parte, porque o emulador nem sempre a mostra.
servico_ativo() { adb shell dumpsys activity services "$PKG" 2>/dev/null | tr -d '\r' | grep -q "ServiceRecord.*LatinIMEKeyboardService"; }
ativo=0
for tentativa in 1 2 3 4 5 6; do
  if servico_ativo; then ativo=1; break; fi
  log "Serviço do IME ainda não ativo (tentativa $tentativa), reabrindo o campo de teste"
  adb shell am start -W -n "$PKG/com.example.DevTypingActivity" >/dev/null 2>&1 || true
  sleep 8
done
sleep 5
log "Serviço do IME ativo antes da medição: $([ "$ativo" = 1 ] && echo sim || echo NÃO)"
log "Teclado visível (mInputShown): $(adb shell dumpsys input_method 2>/dev/null | tr -d '\r' | grep -o 'mInputShown=[a-z]*' | head -1)"
# Captura de tela de teste na galeria: ativa a pílula "Captura de tela" da barra (caso da referência)
log "Preparando captura de tela de teste na galeria:"
python3 scripts/perf/make_png.py "$OUT/Screenshot_teste.png"
adb shell mkdir -p /sdcard/Pictures/Screenshots >/dev/null 2>&1 || true
adb push "$OUT/Screenshot_teste.png" /sdcard/Pictures/Screenshots/Screenshot_teste.png >/dev/null 2>&1 || log "  falha no push"
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/Screenshots/Screenshot_teste.png >/dev/null 2>&1 || true
adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1 || true
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
log "  permissão de imagens: $(adb shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r' | grep -c 'READ_MEDIA_IMAGES: granted=true') concedida(s)"
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb shell am start -W -n "$PKG/com.example.DevTypingActivity" >/dev/null 2>&1 || true
sleep 10
adb exec-out screencap -p > "$OUT/tela-teclado.png" 2>/dev/null || log "  falha na captura de tela"
log "Captura de tela do teclado salva em $OUT/tela-teclado.png ($(stat -c %s "$OUT/tela-teclado.png" 2>/dev/null) bytes)"
snapshot "teclado-visivel"
log "Logs do motor e erros do processo (logcat):"
adb logcat -d 2>/dev/null | tr -d '\r' | grep -iE "NativeEngine|LatinIME|keyboard_core|FATAL|AndroidRuntime|SIGABRT|Abort|dlopen|UnsatisfiedLink|Process .*has died|died" | tail -40 | while read -r l; do log "  ${l:0:220}"; done

log "Armazenamento:"
apk_path=$(adb shell pm path "$PKG" 2>/dev/null | tr -d '\r' | sed 's/^package://' | head -1)
log "  caminho do APK instalado: ${apk_path:-indisponível}"
apk_size=$(adb shell "wc -c < '$apk_path'" 2>/dev/null | tr -d '\r' | tr -d ' ')
log "  tamanho do APK instalado: ${apk_size:-indisponível} bytes"
dados=$(adb shell run-as "$PKG" du -sk . 2>/dev/null | tr -d '\r' | awk '{print $1}')
log "  dados privados do app: ${dados:-indisponível} KB"

log "Estado do teclado:"
adb shell dumpsys input_method 2>/dev/null | tr -d '\r' | grep -E "mCurMethodId|mInputShown" | head -3 | while read -r l; do log "  $l"; done

adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
log "Concluído. Resultados em $OUT/"
exit 0

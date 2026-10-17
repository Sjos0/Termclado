# Termclado 🇧🇷

Teclado Android (IME) para português brasileiro, leve e feito para celulares com pouca memória. Requer **Android 8.0 (API 26)** ou superior.

Versão atual: **1.9.2** (versionCode 12). Pacote: `com.aistudio.tecladoptbr.kxqpva`. Até a v1.9.1 o app se chamava "Teclado Leve PT-BR".

---

## 📱 Recursos

1. **Layout QWERTY em português** com a tecla **ç** na segunda linha.
2. **Acentos por pressão longa:** `a` → á à â ã ä, `e` → é ê è ë, `i` → í ì î ï, `o` → ó ô õ ò ö, `u` → ú ü ù û, `c` → ç, `n` → ñ.
3. **Barra com 3 sugestões**, ranqueadas pela frequência do vocabulário e pela palavra anterior (bigrama).
4. **Autocorreção conservadora:** corrige erros próximos de digitação e palavras sem acento (ex.: `nao` → `não`). Nomes próprios e estrangeirismos listados em `stoplist_names.txt` nunca são alterados. **Backspace logo após uma correção desfaz a alteração.**
5. **Autocapitalização** no início do texto e depois de `.`, `?` e `!`.
6. **Backspace acelerado** ao segurar a tecla.
7. **Enter dinâmico:** mostra Enviar, Buscar, Ir, Próximo, Concluir ou nova linha, conforme o campo.
8. **Digitação por voz (opcional):** usa a API Whisper da Groq. Só funciona com uma chave da Groq configurada no app.
9. **Atualização dentro do app:** baixa a nova versão com barra de progresso, confere se o arquivo é do Termclado, se é mais novo e se tem a mesma assinatura, e então abre a instalação do Android.

Temas, painel de emoji e compartilhar são descritos em `docs/RELEASE_NOTES_v1.8.md`.

---

## 🔒 Privacidade e permissões

O teclado não grava histórico e não envia texto digitado para fora do aparelho. Dicionário e autocorreção funcionam sem internet.

| Permissão | Para quê |
|---|---|
| `INTERNET` | Verificar atualizações no GitHub Releases e transcrever voz pela Groq (a voz só é usada com chave configurada) |
| `RECORD_AUDIO` | Gravar o áudio da digitação por voz |
| `VIBRATE` | Feedback tátil opcional (desligado por padrão) |
| `REQUEST_INSTALL_PACKAGES` | Abrir a instalação de uma atualização baixada |
| `POST_NOTIFICATIONS` | Avisar que há nova versão (Android 13 ou superior) |

Quando você usa a digitação por voz, o áudio gravado é enviado à Groq para transcrição. Sem a chave, nenhum áudio sai do aparelho.

---

## ⚙️ Núcleo nativo (Rust)

- `rust-core/` é o workspace Rust. O CI compila `libkeyboard_core.so` para `arm64-v8a`, `armeabi-v7a` e `x86_64` a cada tag de release, e o APK usa essas bibliotecas.
- O Rust faz o carregamento do dicionário, as sugestões por prefixo, o ranqueamento por bigrama e a verificação de palavra (`isWord`).
- A **autocorreção ainda roda no motor Kotlin** (`CompactTrieEngine`), mesmo com a biblioteca nativa carregada.
- Se a biblioteca nativa não carregar, o app usa só o motor Kotlin.

---

## 📚 Dicionário e licenças

- **Dicionário:** `app/src/main/assets/dict_pt_br.bin`, gerado por `scripts/build_dictionary.py` a partir de `scripts/data/palavras_pt_br.tsv` (cerca de 36 mil palavras).
- **Fontes:** frequência de uso do FrequencyWords (OpenSubtitles 2018, CC BY-SA 4.0) validada com o dicionário do Projeto VERO (LGPL v3.0 / MPL 1.1). Detalhes em `scripts/data/LEIA-ME.md` e `NOTICE`.
- **Dicionário binário:** distribuído sob **CC BY-SA 4.0**, conforme `NOTICE`.
- **Código-fonte:** **MIT** (ver `LICENSE`).

---

## 🛠️ Estrutura

```
.
├── app/src/main/java/com/example/
│   ├── MainActivity.kt            # Tela de configurações, teste e preferências
│   ├── UpdateChecker.kt           # Consulta a última release no GitHub
│   ├── UpdateNotifier.kt          # Aviso de nova versão (no máximo a cada 12 h)
│   ├── ApkDownloader.kt           # Baixa o APK com barra de progresso
│   ├── InstallResultReceiver.kt   # Recebe o resultado da instalação
│   └── keyboard/
│       ├── LatinIMEKeyboardService.kt  # InputMethodService (o teclado)
│       ├── KeyboardView.kt             # Teclas desenhadas em Canvas
│       ├── CandidateView.kt            # Barra de sugestões
│       ├── EmojiPanelView.kt           # Painel de emoji
│       ├── MorePanelView.kt            # Menu da toolbar
│       ├── NativeEngine.kt             # Ponte JNI com libkeyboard_core.so
│       ├── CompactTrieEngine.kt        # Motor em Kotlin (autocorreção e fallback)
│       ├── GroqSpeechClient.kt         # Transcrição por voz (Groq Whisper)
│       ├── VoiceInputHelper.kt         # Gravação de áudio
│       ├── KeyboardPreferences.kt      # Preferências
│       └── KeyboardTheme.kt            # Temas
├── app/src/main/jniLibs/          # libkeyboard_core.so por arquitetura
├── rust-core/src/                 # lib.rs, dictionary.rs, suggest.rs, jni.rs
├── scripts/build_dictionary.py    # Gera o dict_pt_br.bin
│   └── data/                      # Lista de palavras e fontes (LEIA-ME.md)
├── docs/                          # Notas de cada versão
└── stoplist_names.txt             # Palavras que nunca sofrem autocorreção
```

---

## 🚀 Como compilar

Os comandos abaixo são os mesmos que o CI usa, a partir da raiz do repositório:

```bash
# 1. Testes e biblioteca nativa
cargo test --manifest-path rust-core/Cargo.toml
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o app/src/main/jniLibs build -p keyboard-core --release

# 2. APK de depuração
./gradlew assembleDebug
```

O APK de release precisa das variáveis `KEYSTORE_PATH`, `STORE_PASSWORD` e `KEY_PASSWORD`. A chave de assinatura não está no repositório.

---

## 📥 Instalação

1. Baixe o APK da última release no GitHub.
2. Toque no arquivo e confirme a instalação. Se o Android pedir, permita instalar apps desconhecidos.
3. Abra o **Termclado** e siga os passos da tela inicial: ativar o teclado nas configurações do Android e escolhê-lo como teclado atual.

Para atualizar, use o botão de atualização dentro do app. Atualizações mantêm as configurações quando a assinatura é a mesma.

---

## 📊 Medição de memória

Com o teclado aberto, meça o consumo do processo pelo `adb`:

```bash
adb shell dumpsys meminfo com.aistudio.tecladoptbr.kxqpva
```

Procure a linha `TOTAL PSS`. Os valores variam conforme o aparelho e a versão do Android.

---

## 🗂️ Versões anteriores

Cada versão tem notas em `docs/`. As releases publicadas estão na página de releases do GitHub. Os APKs de versões que não têm release (1.3, 1.4 e 1.8 de depuração) ficam na branch `apk-historico`, com índice e SHA-256 em `APK/INDICE.md`.

## 🧭 Pendências conhecidas

- **Autocorreção nativa:** a função `nativeGetAutoCorrection` existe em Rust, mas não é usada. Trocar o motor exige testes no aparelho, porque o resultado precisa ser igual ao do motor Kotlin.
- **Dicionário carregado duas vezes** (Kotlin e Rust). Só dá para remover depois da troca acima.
- **Busca por prefixo:** hoje varre a lista inteira. Como o dicionário já está ordenado, uma busca binária deixaria a consulta mais rápida (cerca de 334 µs por consulta no host com 36 mil palavras).

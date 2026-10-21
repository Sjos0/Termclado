package com.example.keyboard

import com.example.onTap

import com.example.UpdateNotifier
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
// import androidx.core.content.ContextCompat
import android.widget.Toast
import android.view.Gravity
import android.graphics.Color
import android.widget.TextView
import android.widget.FrameLayout
import android.os.Looper
import android.os.Handler
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import android.content.ClipDescription
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

class LatinIMEKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private data class AutoCorrectionRecord(
        val original: String,
        val corrected: String
    )

    private lateinit var prefs: KeyboardPreferences
    private var voiceHelper: VoiceInputHelper? = null
    private var transcribing = false
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recordingOverlay: FrameLayout? = null
    private lateinit var contentFrame: FrameLayout
    private lateinit var morePanel: MorePanelView
    private lateinit var emojiPanel: EmojiPanelView
    private var panelMode: PanelMode = PanelMode.KEYBOARD

    private enum class PanelMode { KEYBOARD, MORE, EMOJI }

    /** Pilha simples de desfazer (últimas operações de texto). */
    private data class UndoEntry(val kind: Kind, val text: String, val beforeLen: Int = 0) {
        enum class Kind { INSERT, DELETE, REPLACE }
    }
    private val undoStack = ArrayDeque<UndoEntry>(16)
    private var amplitudeView: TextView? = null
    private var statusLabel: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            val amp = voiceHelper?.amplitude() ?: 0f
            amplitudeView?.let { v ->
                val scale = 1f + amp * 0.55f
                v.scaleX = scale
                v.scaleY = scale
                val alpha = (0.45f + amp * 0.55f).coerceIn(0.4f, 1f)
                v.alpha = alpha
            }
            if (voiceHelper?.isRecording == true) {
                mainHandler.postDelayed(this, 80L)
            }
        }
    }
    private lateinit var keyboardView: KeyboardView
    private lateinit var candidateView: CandidateView

    private val composingWord = StringBuilder()
    // #44: sugestão de autopreenchimento do sistema (API 30+), usada ao tocar na pílula
    private var pendingAutofill: InlineSuggestion? = null
    private val userDict get() = LearningStore.dictionary(this)
    private val personalBigrams get() = LearningStore.bigrams(this)
    // Seleção atual informada pelo editor (-1 = desconhecida), para o backspace apagar o intervalo
    private var selStart = -1
    private var selEnd = -1
    private var previousWord: String = ""
    private var lastAutoCorrection: AutoCorrectionRecord? = null

    private var lastShiftPressTime = 0L

    override fun onCreate() {
        super.onCreate()
        EmojiSuggestions.init(this)
        prefs = KeyboardPreferences(this)
        NativeEngine.init(this)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#263238"))
        }

        candidateView = CandidateView(this).apply {
            setToolbarOrder(CandidateView.parseOrder(prefs.toolbarOrder))
            onSuggestionClickListener = { chosen ->
                commitSuggestion(chosen)
            }
            onToolbarOrderChanged = { order ->
                prefs.toolbarOrder = CandidateView.serializeOrder(order)
            }
            onGridLongPress = { toggleMorePanel() }
            onScreenshotClick = { insertScreenshot() }
            onAutofillClick = { fillWithAutofill() }
            onToolbarActionClickListener = { action ->
                when (action) {
                    CandidateView.ToolbarAction.MENU -> toggleMorePanel()
                    CandidateView.ToolbarAction.CURSOR -> {
                        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
                        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
                    }
                    CandidateView.ToolbarAction.CLIPBOARD -> pasteClipboard()
                    // Tradução ainda não existe: avisa em vez de abrir o app por engano
                    CandidateView.ToolbarAction.TRANSLATE -> Toast.makeText(this@LatinIMEKeyboardService, "Tradução ainda não disponível", Toast.LENGTH_SHORT).show()
                    CandidateView.ToolbarAction.SETTINGS -> openMainActivity()
                    CandidateView.ToolbarAction.MIC -> onMicTapped()
                }
            }
        }

        keyboardView = KeyboardView(this).apply {
            listener = this@LatinIMEKeyboardService
            vibrateEnabled = prefs.vibrateOnPress
        }

        morePanel = MorePanelView(this).apply {
            visibility = android.view.View.GONE
            onAction = { act ->
                when (act) {
                    MorePanelView.Action.THEME -> cycleTheme()
                    MorePanelView.Action.EMOJI -> showEmojiPanel()
                    MorePanelView.Action.SHARE -> shareCurrentText()
                    MorePanelView.Action.UNDO -> performUndo()
                }
            }
        }

        emojiPanel = EmojiPanelView(this).apply {
            visibility = android.view.View.GONE
            onEmoji = { emoji ->
                pushUndoInsert(emoji)
                currentInputConnection?.commitText(emoji, 1)
            }
            onBack = { showMorePanel() }
        }

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        // Altura mínima razoável para o teclado
        val density = resources.displayMetrics.density
        val kbH = (240 * density).toInt()
        keyboardView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            kbH
        )
        morePanel.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            kbH
        )
        emojiPanel.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            kbH
        )
        contentFrame.addView(keyboardView)
        contentFrame.addView(morePanel)
        contentFrame.addView(emojiPanel)

        root.addView(candidateView)
        root.addView(contentFrame)

        applyCurrentTheme()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        attribute?.contentMimeTypes = arrayOf("image/png", "image/jpeg", "image/gif", "image/webp")
        composingWord.setLength(0)
        previousWord = ""
        selStart = -1
        selEnd = -1
        clearAutofill()
        lastAutoCorrection = null
        // Campo novo: a barra mostra as previsões iniciais, sem resto da palavra anterior
        updateCandidates()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (composingWord.isEmpty()) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(composingWord.length, 0)?.toString().orEmpty()
        val movedAway = newSelStart != newSelEnd ||
            ComposingRules.compositionElsewhere(candidatesEnd, newSelEnd) ||
            !ComposingRules.stillAtCursor(before, composingWord.toString())
        if (movedAway) {
            // O usuário foi para outro ponto do texto: encerra a palavra no lugar onde ela está
            composingWord.setLength(0)
            lastAutoCorrection = null
            ic.finishComposingText()
            updateCandidates()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        UpdateNotifier.maybeCheck(this, voiceScope)
        keyboardView.vibrateEnabled = prefs.vibrateOnPress
        applyCurrentTheme()
        if (panelMode != PanelMode.KEYBOARD) showKeyboardPanel()

        if (info != null) {
            updateEnterActionLabel(info)
            checkAutoCapitalization(info)
        }
        refreshScreenshot()
        updateCandidates()
    }

    private fun updateEnterActionLabel(info: EditorInfo) {
        val action = info.imeOptions and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv())

        keyboardView.actionType = when (action) {
            EditorInfo.IME_ACTION_SEARCH -> KeyboardView.ACTION_TYPE_SEARCH
            EditorInfo.IME_ACTION_SEND -> KeyboardView.ACTION_TYPE_SEND
            EditorInfo.IME_ACTION_GO -> KeyboardView.ACTION_TYPE_GO
            EditorInfo.IME_ACTION_DONE -> KeyboardView.ACTION_TYPE_DONE
            else -> KeyboardView.ACTION_TYPE_ENTER
        }
    }

    private fun checkAutoCapitalization(info: EditorInfo? = currentInputEditorInfo) {
        if (!prefs.autoCapitalization || keyboardView.shiftState == KeyboardView.SHIFT_LOCKED) {
            return
        }

        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""

        // Autocapitalização no início do texto ou após '.', '?', '!' seguido de espaço ou quebra de linha
        val shouldCapitalize = when {
            textBefore.isEmpty() -> true
            textBefore.endsWith("\n") -> true
            textBefore.length >= 2 && (textBefore[0] == '.' || textBefore[0] == '?' || textBefore[0] == '!') && textBefore[1] == ' ' -> true
            else -> false
        }

        keyboardView.shiftState = when {
            !shouldCapitalize -> KeyboardView.SHIFT_OFF
            textBefore.isEmpty() -> KeyboardView.SHIFT_LOCKED // campo vazio: abre tudo em maiúsculas, ativado
            else -> KeyboardView.SHIFT_ON
        }
    }

    override fun onKey(code: Int, text: String?) {
        if (::candidateView.isInitialized) candidateView.setToolsOpen(false)
        val ic = currentInputConnection ?: return
        lastAutoCorrection = null // Qualquer digitação descarta a janela de desfazer autocorreção

        if (code == KeyboardView.CODE_SPACE) {
            handleSpace()
            return
        }

        if (text != null) {
            val charToInsert = if (keyboardView.shiftState != KeyboardView.SHIFT_OFF) {
                text.uppercase()
            } else {
                text.lowercase()
            }

            if (Character.isLetter(code)) {
                composingWord.append(charToInsert)
                ic.setComposingText(composingWord.toString(), 1)
                updateCandidates()
            } else {
                commitComposingText()
                ic.commitText(charToInsert, 1)
                updateCandidates()
            }

            // Se o shift estiver apenas temporariamente ativo, desativa após digitar a letra
            if (keyboardView.shiftState == KeyboardView.SHIFT_ON) {
                keyboardView.shiftState = KeyboardView.SHIFT_OFF
            }
        }
    }

    override fun onReplaceKey(text: String) {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.length - 1)
            composingWord.append(text)
            ic.setComposingText(composingWord.toString(), 1)
            updateCandidates()
        } else {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(text, 1)
        }
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val typed = composingWord.toString()

        if (typed.isNotEmpty()) {
            var wordToCommit = typed

            // Correção automática conservadora
            if (prefs.autoCorrection) {
                val correction = NativeEngine.getAutoCorrection(previousWord, typed)
                if (correction != null && !correction.equals(typed, ignoreCase = true)) {
                    wordToCommit = correction
                    // Registra para permitir desfazer com Backspace imediato
                    lastAutoCorrection = AutoCorrectionRecord(original = typed, corrected = correction)
                }
            }

            ic.commitText("$wordToCommit ", 1)
            recordBigram(previousWord, wordToCommit)
            previousWord = wordToCommit
            composingWord.setLength(0)
        } else {
            ic.commitText(" ", 1)
        }

        checkAutoCapitalization()
        updateCandidates()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return

        // REQUISITO: "Backspace logo após uma correção desfaz essa correção."
        if (lastAutoCorrection != null) {
            val record = lastAutoCorrection!!
            lastAutoCorrection = null

            // Apagar a palavra corrigida e o espaço inserido
            val deleteCount = record.corrected.length + 1
            ic.deleteSurroundingText(deleteCount, 0)

            // Restaurar exatamente o que o usuário havia digitado originalmente
            composingWord.setLength(0)
            composingWord.append(record.original)
            ic.setComposingText(composingWord.toString(), 1)
            updateCandidates()
            return
        }

        // Seleção ativa: o backspace apaga o intervalo inteiro e deixa o cursor no início dele
        if (selStart >= 0 && selEnd > selStart) {
            composingWord.setLength(0)
            ic.commitText("", 1)
            selEnd = selStart
            updateCandidates()
            return
        }

        if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.length - 1)
            if (composingWord.isNotEmpty()) {
                ic.setComposingText(composingWord.toString(), 1)
            } else {
                // Limpa composição sem commit vazio (evita efeitos colaterais no editor)
                ic.setComposingText("", 0)
                ic.finishComposingText()
            }
            updateCandidates()
        } else {
            // Só InputConnection — NUNCA KEYCODE_DEL: em vários apps o DEL com campo
            // vazio (ou o último caractere) faz o sistema fechar o teclado.
            // Apaga o último caractere visível inteiro (emoji = 2 code units; ZWJ/modificadores juntos)
            val before = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
            val n = Graphemes.lastClusterLength(before)
            if (n > 0) ic.deleteSurroundingText(n, 0)
            // Se já está vazio, não faz nada e mantém o IME visível.
        }

        checkAutoCapitalization()
    }

    override fun onShift() {
        val now = System.currentTimeMillis()
        keyboardView.shiftState = when (keyboardView.shiftState) {
            KeyboardView.SHIFT_OFF -> KeyboardView.SHIFT_ON
            KeyboardView.SHIFT_ON -> {
                if (now - lastShiftPressTime < 350) {
                    KeyboardView.SHIFT_LOCKED // Duplo clique ativa Caps Lock
                } else {
                    KeyboardView.SHIFT_OFF
                }
            }
            KeyboardView.SHIFT_LOCKED -> KeyboardView.SHIFT_OFF
            else -> KeyboardView.SHIFT_OFF
        }
        lastShiftPressTime = now
    }

    override fun onActionKey() {
        val ic = currentInputConnection ?: return
        commitComposingText()

        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE

        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }

        checkAutoCapitalization()
    }

    private fun commitSuggestion(chosen: String) {
        val ic = currentInputConnection ?: return
        lastAutoCorrection = null
        ic.commitText("$chosen ", 1)
        learnChosen(chosen)
        recordBigram(previousWord, chosen)
        previousWord = chosen
        composingWord.setLength(0)

        checkAutoCapitalization()
        updateCandidates()
    }

    @Volatile private var currentShot: ScreenshotSource.Shot? = null

    /** Busca a captura recente fora da thread principal e atualiza a pílula. */
    private fun refreshScreenshot() {
        if (!::candidateView.isInitialized) return
        Thread {
            val shot = ScreenshotSource.latestRecent(this)
            val thumb = shot?.let { ScreenshotSource.thumbnail(this, it) }
            mainHandler.post {
                currentShot = shot
                candidateView.setScreenshot(if (shot != null) "Captura de tela" else null, thumb)
            }
        }.start()
    }

    /** Envia a captura para o app em foco, se ele aceitar imagens; senão avisa. */
    private fun insertScreenshot() {
        val shot = currentShot ?: return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        // Declara os tipos de imagem no EditorInfo deste campo (sem isso o app não recebe imagens)
        androidx.core.view.inputmethod.EditorInfoCompat.setContentMimeTypes(
            info, arrayOf("image/png", "image/jpeg", "image/gif", "image/webp"))
        val content = InputContentInfoCompat(shot.uri, ClipDescription("Captura de tela", arrayOf("image/png")), null)
        val ok = InputConnectionCompat.commitContent(
            ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
        )
        android.util.Log.i("ScreenshotPill", "commitContent=$ok uri=${shot.uri}")
        if (ok) return
        // Fallback: copia a imagem para a área de transferência e tenta colar no campo
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newUri(contentResolver, "Captura de tela", shot.uri))
        val pasted = ic.performContextMenuAction(android.R.id.paste)
        android.util.Log.i("ScreenshotPill", "fallback paste=$pasted")
        if (!pasted) Toast.makeText(this, "Imagem copiada: cole no campo", Toast.LENGTH_SHORT).show()
    }

    private fun commitComposingText() {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            val typed = composingWord.toString()
            ic.commitText(typed, 1)
            previousWord = typed
            composingWord.setLength(0)
            // Sem isso, as sugestões da palavra já enviada continuavam na barra (parecia duplicada)
            updateCandidates()
        }
    }

    /** Gate: o preenchimento por sugestão inline só existe a partir do Android 11 (API 30). */
    private val inlineAutofillSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!inlineAutofillSupported) return null
        val d = resources.displayMetrics.density
        // Tamanho máximo = pílula da barra (151 x 34 dp); pede só 1 sugestão
        val spec = InlinePresentationSpec.Builder(Size(0, 0), Size((151 * d).toInt(), (34 * d).toInt())).build()
        return InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(1).build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inlineAutofillSupported) return false
        // Nunca lê nem guarda o conteúdo (senha/e-mail): só mantém a referência para aplicar o toque
        pendingAutofill = response.inlineSuggestions.firstOrNull()
        if (::candidateView.isInitialized) {
            candidateView.setAutofillPill(if (pendingAutofill != null) "Preencher automaticamente" else null)
        }
        return true
    }

    private fun clearAutofill() {
        pendingAutofill = null
        if (::candidateView.isInitialized) candidateView.setAutofillPill(null)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun fillWithAutofill() {
        if (!inlineAutofillSupported) return
        val s = pendingAutofill ?: return
        val d = resources.displayMetrics.density
        s.inflate(this, Size((151 * d).toInt(), (34 * d).toInt()), mainExecutor) { view ->
            // A sugestão do próprio sistema aplica o preenchimento ao receber o clique
            view.performClick()
            clearAutofill()
        }
    }

    private fun updateCandidates() {
        if (!::candidateView.isInitialized) return
        val prefix = composingWord.toString()
        if (prefix.isNotEmpty()) {
            // Palavra desconhecida digitada aparece primeiro (#43); depois aprendidas, depois o motor (com peso pessoal)
            val native = rankWithPersonal(NativeEngine.getSuggestions(previousWord, prefix), prefix)
            val learned = userDict.suggestionsFor(prefix, 2)
            val unknownTyped = if (prefix.length >= 2 && !NativeEngine.isWord(prefix) &&
                !userDict.contains(prefix) && !isSensitiveField()) prefix else null
            val text = buildList {
                unknownTyped?.let { add(it) }
                for (w in learned) if (w !in this && w !in native) add(w)
                for (w in native) if (w !in this) add(w)
            }.take(3)
            // Como o Gboard: até 2 palavras + até 2 emojis quando a palavra tem emoji correspondente
            val emojis = EmojiSuggestions.forWord(prefix).take(2)
            val list = if (emojis.isEmpty()) text.take(3) else text.take(2) + emojis
            candidateView.setSuggestions(list, composing = true)
        } else {
            // Previsão da próxima palavra: as mais usadas pelo usuário primeiro (#45), completadas pelo motor
            val personal = personalBigrams.topNext(previousWord, 3)
            val native = NativeEngine.getSuggestions(previousWord, "")
            candidateView.setSuggestions((personal + native).distinct().take(3), composing = false)
        }
    }

    /** Reordena o ranking nativo com o peso pessoal, em Kotlin (o Rust não muda). */
    private fun rankWithPersonal(native: List<String>, prefix: String): List<String> {
        val scored = native.mapIndexed { i, w ->
            w to ((native.size - i) * 10_000 + personalBigrams.boost(previousWord, w))
        }.sortedByDescending { it.second }.map { it.first }
        val extras = personalBigrams.topNext(previousWord, 5)
            .filter { it.startsWith(prefix, ignoreCase = true) && it !in scored }
        return (scored + extras).distinct()
    }

    /** Campo de senha ou e-mail: nada é aprendido nem registrado (REQ-LRN-006 / REQ-AI-006). */
    private fun isSensitiveField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> true
            else -> false
        }
    }

    private fun recordBigram(prev: String, next: String) {
        if (prev.isBlank() || next.isBlank() || !next.all { it.isLetter() } || isSensitiveField()) return
        personalBigrams.record(prev.trim(), next.trim())
    }

    /** Só aprende no toque explícito do usuário numa sugestão de palavra (REQ-LRN-003). */
    private fun learnChosen(chosen: String) {
        if (chosen.isNotEmpty() && chosen.all { it.isLetter() } && !isSensitiveField()) userDict.learn(chosen)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        commitComposingText()
        lastAutoCorrection = null
        clearAutofill()
        // Teclado escondido: cancela a gravação para o microfone não continuar aberto
        if (voiceHelper?.isRecording == true) {
            mainHandler.removeCallbacks(amplitudeRunnable)
            voiceHelper?.cancel()
            hideRecordingOverlay()
        }
    }


    private fun onMicTapped() {
        if (transcribing) return // ainda transcrevendo a gravação anterior
        if (voiceHelper?.isRecording == true) {
            finishRecordingAndTranscribe()
            return
        }
        val key = prefs.groqApiKey
        if (key.isBlank()) {
            Toast.makeText(this, "Configure a chave da API Groq nas configurações do teclado.", Toast.LENGTH_LONG).show()
            try {
                val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (_: Exception) {
            }
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Permissão de microfone necessária. Abra as configurações do app e conceda o acesso.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("request_mic_permission", true)
                }
                startActivity(intent)
            } catch (_: Exception) {
            }
            return
        }
        startVoiceRecording()
    }

    private fun startVoiceRecording() {
        try {
            if (voiceHelper == null) voiceHelper = VoiceInputHelper(this)
            // Mostra a UI primeiro — se a gravação falhar, o usuário vê e pode cancelar
            showRecordingOverlay()
            voiceHelper!!.start()
            mainHandler.post(amplitudeRunnable)
        } catch (e: Exception) {
            mainHandler.removeCallbacks(amplitudeRunnable)
            voiceHelper?.cancel()
            hideRecordingOverlay()
            Toast.makeText(this, "Não foi possível iniciar o microfone: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishRecordingAndTranscribe() {
        // Toque duplo no ■: só a primeira chamada vale, a gravação já foi encerrada
        if (transcribing || voiceHelper?.isRecording != true) return
        mainHandler.removeCallbacks(amplitudeRunnable)
        val file = voiceHelper?.stop()
        statusLabel?.text = "Transcrevendo…"
        amplitudeView?.text = "…"
        if (file == null) {
            hideRecordingOverlay()
            Toast.makeText(this, "Gravação inválida.", Toast.LENGTH_SHORT).show()
            return
        }
        val apiKey = prefs.groqApiKey
        transcribing = true
        voiceScope.launch {
            val result = try {
                GroqSpeechClient.transcribe(apiKey, file)
            } finally {
                transcribing = false
                try {
                    file.delete()
                } catch (_: Exception) {
                }
            }
            hideRecordingOverlay()
            when (result) {
                is GroqSpeechClient.Result.Success -> {
                    commitComposingText()
                    currentInputConnection?.commitText(result.text, 1)
                    Toast.makeText(this@LatinIMEKeyboardService, "Texto inserido", Toast.LENGTH_SHORT).show()
                }
                is GroqSpeechClient.Result.Error -> {
                    Toast.makeText(this@LatinIMEKeyboardService, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showRecordingOverlay() {
        hideRecordingOverlay()
        val density = resources.displayMetrics.density
        // Altura estável: evita View.GONE nas teclas (encolhe a janela do IME e o overlay some).
        val kbH = when {
            contentFrame.height > 0 -> contentFrame.height
            keyboardView.height > 0 -> keyboardView.height
            keyboardView.layoutParams?.height?.let { it > 0 } == true -> keyboardView.layoutParams.height
            else -> (240 * density).toInt()
        }
        contentFrame.minimumHeight = kbH
        contentFrame.layoutParams = (contentFrame.layoutParams as LinearLayout.LayoutParams).apply {
            height = kbH
            weight = 0f
        }
        // INVISIBLE mantém o espaço de layout; overlay cobre por cima
        keyboardView.visibility = View.INVISIBLE
        morePanel.visibility = View.GONE
        emojiPanel.visibility = View.GONE

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1B2327"))
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
        }
        val title = TextView(this).apply {
            text = "Ouvindo… toque no ■ para enviar"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        statusLabel = title
        val ring = TextView(this).apply {
            text = "🎤"
            textSize = 36f
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
            setTextColor(Color.parseColor("#50B4CE"))
        }
        amplitudeView = ring
        val stopBtn = TextView(this).apply {
            text = "■"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setPadding((40 * density).toInt(), (14 * density).toInt(), (40 * density).toInt(), (14 * density).toInt())
            contentDescription = "Parar gravação e enviar"
            onTap { finishRecordingAndTranscribe() }
        }
        val cancel = TextView(this).apply {
            text = "Cancelar"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, (12 * density).toInt(), 0, 0)
            onTap {
                mainHandler.removeCallbacks(amplitudeRunnable)
                voiceHelper?.cancel()
                hideRecordingOverlay()
            }
        }
        column.addView(title)
        column.addView(ring)
        column.addView(stopBtn)
        column.addView(cancel)
        overlay.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        contentFrame.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        recordingOverlay = overlay
        contentFrame.requestLayout()
        contentFrame.invalidate()
    }

    private fun hideRecordingOverlay() {
        val overlay = recordingOverlay
        if (overlay != null) {
            try {
                (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
            } catch (_: Exception) {
            }
        }
        recordingOverlay = null
        amplitudeView = null
        statusLabel = null
        // Restaura altura flexível do contentFrame e o painel ativo
        if (::contentFrame.isInitialized) {
            contentFrame.minimumHeight = 0
            contentFrame.layoutParams = (contentFrame.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 1f
            }
        }
        if (::keyboardView.isInitialized) {
            when (panelMode) {
                PanelMode.MORE -> {
                    morePanel.visibility = View.VISIBLE
                    keyboardView.visibility = View.GONE
                    emojiPanel.visibility = View.GONE
                }
                PanelMode.EMOJI -> {
                    emojiPanel.visibility = View.VISIBLE
                    keyboardView.visibility = View.GONE
                    morePanel.visibility = View.GONE
                }
                else -> {
                    keyboardView.visibility = View.VISIBLE
                    morePanel.visibility = View.GONE
                    emojiPanel.visibility = View.GONE
                    panelMode = PanelMode.KEYBOARD
                }
            }
            contentFrame.requestLayout()
        }
    }


    private fun toggleMorePanel() {
        if (panelMode == PanelMode.MORE || panelMode == PanelMode.EMOJI) {
            showKeyboardPanel()
        } else {
            showMorePanel()
        }
    }

    private fun showKeyboardPanel() {
        panelMode = PanelMode.KEYBOARD
        keyboardView.visibility = android.view.View.VISIBLE
        morePanel.visibility = android.view.View.GONE
        emojiPanel.visibility = android.view.View.GONE
        candidateView.menuOpen = false
    }

    private fun showMorePanel() {
        panelMode = PanelMode.MORE
        keyboardView.visibility = android.view.View.GONE
        morePanel.visibility = android.view.View.VISIBLE
        emojiPanel.visibility = android.view.View.GONE
        candidateView.menuOpen = true
    }

    private fun showEmojiPanel() {
        panelMode = PanelMode.EMOJI
        keyboardView.visibility = android.view.View.GONE
        morePanel.visibility = android.view.View.GONE
        emojiPanel.visibility = android.view.View.VISIBLE
        candidateView.menuOpen = true
    }

    private fun applyCurrentTheme() {
        val palette = KeyboardTheme.get(prefs.themeId)
        if (::keyboardView.isInitialized) keyboardView.applyPalette(palette)
        if (::candidateView.isInitialized) candidateView.applyPalette(palette)
        if (::morePanel.isInitialized) morePanel.applyPalette(palette)
        if (::emojiPanel.isInitialized) emojiPanel.applyPalette(palette)
        if (::contentFrame.isInitialized) {
            // fundo do root já no candidate; contentFrame transparente
        }
    }

    private fun cycleTheme() {
        prefs.themeId = KeyboardTheme.nextId(prefs.themeId)
        val palette = KeyboardTheme.get(prefs.themeId)
        applyCurrentTheme()
        android.widget.Toast.makeText(this, "Tema: ${palette.name}", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun pasteClipboard() {
        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).text?.toString()
                if (!pasteText.isNullOrEmpty()) {
                    commitComposingText()
                    pushUndoInsert(pasteText)
                    currentInputConnection?.commitText(pasteText, 1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun openMainActivity() {
        try {
            val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun shareCurrentText() {
        try {
            val ic = currentInputConnection ?: return
            val selected = ic.getSelectedText(0)?.toString()
            val text = if (!selected.isNullOrEmpty()) {
                selected
            } else {
                val before = ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
                val after = ic.getTextAfterCursor(500, 0)?.toString().orEmpty()
                (before + after).trim()
            }
            if (text.isEmpty()) {
                android.widget.Toast.makeText(this, "Nada para compartilhar", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(android.content.Intent.createChooser(intent, "Compartilhar texto").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            showKeyboardPanel()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Não foi possível compartilhar", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun pushUndoInsert(text: String) {
        if (text.isEmpty()) return
        if (undoStack.size >= 16) undoStack.removeFirst()
        undoStack.addLast(UndoEntry(UndoEntry.Kind.INSERT, text))
    }

    private fun pushUndoDelete(text: String) {
        if (text.isEmpty()) return
        if (undoStack.size >= 16) undoStack.removeFirst()
        undoStack.addLast(UndoEntry(UndoEntry.Kind.DELETE, text))
    }

    private fun pushUndoReplace(original: String, corrected: String) {
        if (undoStack.size >= 16) undoStack.removeFirst()
        undoStack.addLast(UndoEntry(UndoEntry.Kind.REPLACE, original, corrected.length))
    }

    private fun performUndo() {
        val ic = currentInputConnection ?: return
        val entry = undoStack.removeLastOrNull()
        if (entry == null) {
            // Fallback: desfaz auto-correção registrada
            val last = lastAutoCorrection
            if (last != null) {
                val before = ic.getTextBeforeCursor(last.corrected.length, 0)?.toString().orEmpty()
                if (before.endsWith(last.corrected)) {
                    ic.deleteSurroundingText(last.corrected.length, 0)
                    ic.commitText(last.original, 1)
                    lastAutoCorrection = null
                    showKeyboardPanel()
                    return
                }
            }
            android.widget.Toast.makeText(this, "Nada para desfazer", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        when (entry.kind) {
            UndoEntry.Kind.INSERT -> {
                val before = ic.getTextBeforeCursor(entry.text.length, 0)?.toString().orEmpty()
                if (before.endsWith(entry.text)) {
                    ic.deleteSurroundingText(entry.text.length, 0)
                }
            }
            UndoEntry.Kind.DELETE -> {
                ic.commitText(entry.text, 1)
            }
            UndoEntry.Kind.REPLACE -> {
                // text = original; beforeLen = length of corrected that was inserted
                val before = ic.getTextBeforeCursor(entry.beforeLen, 0)?.toString().orEmpty()
                if (before.length == entry.beforeLen) {
                    ic.deleteSurroundingText(entry.beforeLen, 0)
                    ic.commitText(entry.text, 1)
                }
            }
        }
        showKeyboardPanel()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(amplitudeRunnable)
        voiceHelper?.cancel()
        hideRecordingOverlay()
        voiceScope.cancel()
        super.onDestroy()
    }

}

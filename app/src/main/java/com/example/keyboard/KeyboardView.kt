package com.example.keyboard

import com.example.TapGate
import android.os.SystemClock

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.R
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(code: Int, text: String?)
        fun onReplaceKey(text: String)
        fun onActionKey()
        fun onBackspace()
        fun onShift()
    }

    companion object {
        // dp medidos na captura do Gboard (720 px de largura = 400 dp): topo e altura de cada linha de teclas
        const val KEYS_TOTAL_DP = 241.4f
        val ROW_TOPS_DP = floatArrayOf(8.7f, 51.4f, 97.6f, 143.7f, 189.8f)
        val ROW_HEIGHTS_DP = floatArrayOf(36.7f, 38.9f, 38.9f, 38.9f, 42.2f)
        const val CODE_SHIFT = -1
        const val CODE_BACKSPACE = -2
        const val CODE_ENTER = -3
        const val CODE_MODE_CHANGE = -4
        const val CODE_MORE_SYMBOLS = -5
        const val CODE_EMOJI = -6
        const val CODE_SPACE = 32

        const val SHIFT_OFF = 0
        const val SHIFT_ON = 1
        const val SHIFT_LOCKED = 2

        const val MODE_TEXT = 0
        const val MODE_SYMBOLS = 1
        const val MODE_MORE_SYMBOLS = 2

        const val ACTION_TYPE_SEARCH = 1
        const val ACTION_TYPE_SEND = 2
        const val ACTION_TYPE_GO = 3
        const val ACTION_TYPE_DONE = 4
        const val ACTION_TYPE_ENTER = 5
    }

    var listener: OnKeyboardActionListener? = null

    var shiftState: Int = SHIFT_OFF
        set(value) {
            field = value
            updateLabelsForShift()
            invalidate()
        }

    var keyboardMode: Int = MODE_TEXT
        set(value) {
            field = value
            buildKeyboardLayout()
            invalidate()
        }

    var actionType: Int = ACTION_TYPE_SEARCH
        set(value) {
            field = value
            invalidate()
        }

    var vibrateEnabled: Boolean = false

    fun applyPalette(p: KeyboardTheme.Palette) {
        backgroundPaint.color = p.background
        keyPaint.color = p.key
        specialKeyPaint.color = p.specialKey
        symbolKeyPaint.color = p.specialKey
        pressedKeyPaint.color = p.pressedKey
        actionKeyPaint.color = p.actionKey
        actionPressedPaint.color = p.actionPressed
        textPaint.color = p.text
        specialTextPaint.color = p.text
        iconPaint.color = p.text
        iconFillPaint.color = p.text
        emojiBorderPaint.color = p.emojiBorder
        invalidate()
    }

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Paleta de cores oficial baseada na referência (fundo #263238, tecla #404A50, função #313C42, emoji border #7FCFFF)
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#263238")
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#404A50")
        style = Paint.Style.FILL
    }
    private val specialKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#313C42")
        style = Paint.Style.FILL
    }
    private val symbolKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#313C42")
        style = Paint.Style.FILL
    }
    private val pressedKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546068")
        style = Paint.Style.FILL
    }
    private val actionKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6EACA8")
        style = Paint.Style.FILL
    }
    private val actionPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A9592")
        style = Paint.Style.FILL
    }
    private val emojiBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FCFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val specialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B2327")
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 8f, Color.parseColor("#90000000"))
    }

    // Objetos reutilizáveis para desenho (Zero Garbage Collection para digitação contínua)
    private val tempRect = RectF()
    private val tempPath = Path()

    data class Key(
        val code: Int,
        var label: String,
        val accents: List<String> = emptyList(),
        val widthWeight: Float = 1.0f,
        val isSpecial: Boolean = false,
        val isAction: Boolean = false,
        val isEmoji: Boolean = false,
        var rect: RectF = RectF()
    )

    private val keys = ArrayList<Key>(50)

    private data class PointerInfo(
        var key: Key,
        var startX: Float,
        var startY: Float,
        var isLongPressTriggered: Boolean = false
    )

    private val activePointers = HashMap<Int, PointerInfo>()
    // Rastreamento de ponteiros por tecla (um dedo por tecla)
    private val activeByKey = HashMap<Key, Int>()

    // Popup de acentos e caracteres secundários
    private var popupActive = false
    private var popupKey: Key? = null
    private val popupRects = ArrayList<Pair<String, RectF>>()
    private var selectedPopupIndex = -1
    private var popupPointerId = -1

    private val handler = Handler(Looper.getMainLooper())
    private var backspaceDelay = 350L
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            triggerKeyVibration()
            backspaceDelay = (backspaceDelay * 0.82).toLong().coerceAtLeast(25L)
            handler.postDelayed(this, backspaceDelay)
        }
    }

    private var pendingLongPressPointerId: Int = -1
    private val longPressDelay = (ViewConfiguration.getLongPressTimeout() * 0.9).toLong().coerceAtLeast(420L)
    private val longPressRunnable = Runnable {
        val info = activePointers[pendingLongPressPointerId]
        if (info != null && info.key.accents.isNotEmpty()) {
            info.isLongPressTriggered = true
            popupActive = true
            popupKey = info.key
            popupPointerId = pendingLongPressPointerId
            selectedPopupIndex = -1
            calculatePopupLayout(info.key)
            triggerKeyVibration()
            invalidate()
        }
    }

    private fun triggerKeyVibration() {
        if (!vibrateEnabled) return
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(10)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        // Altura das teclas igual à do Gboard medida no mesmo aparelho (1,8 px/dp): 241,4 dp
        val height = (KEYS_TOTAL_DP * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildKeyboardLayout()
    }

    private fun updateLabelsForShift() {
        for (k in keys) {
            if (k.code > 0 && Character.isLetter(k.code)) {
                k.label = if (shiftState != SHIFT_OFF) {
                    k.label.uppercase()
                } else {
                    k.label.lowercase()
                }
            }
        }
    }

    private fun buildKeyboardLayout() {
        keys.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        // Geometria medida no Gboard (1,8 px/dp): vão horizontal de 8 px = 4,44 dp
        val paddingH = 4.44f * density
        val rows = when (keyboardMode) {
            MODE_TEXT -> getTextRows()
            MODE_SYMBOLS -> getSymbolRows()
            MODE_MORE_SYMBOLS -> getMoreSymbolRows()
            else -> getTextRows()
        }

        val numRows = rows.size
        // Com 5 linhas usa topo e altura de cada linha do Gboard; com outro número, divide por igual
        val useTable = numRows == 5
        val uniformGap = 7.2f * density
        val uniformTop = ROW_TOPS_DP[0] * density
        val uniformHeight = (h - uniformTop - 9.4f * density - uniformGap * (numRows - 1)) / numRows

        // Largura base de tecla para linhas de 10 teclas (linhas 1 e 2)
        val standardAvailableWidth = w - (paddingH * 11)
        val standardKeyWidth = standardAvailableWidth / 10f

        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            val rowTop = if (useTable) ROW_TOPS_DP[rowIndex] * density else uniformTop + rowIndex * (uniformHeight + uniformGap)
            val rowHeight = if (useTable) ROW_HEIGHTS_DP[rowIndex] * density else uniformHeight
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()

            // Se for a linha 3 (a..l com 9 teclas de peso 1.0 no modo texto),
            // usa a largura padrão das teclas e centraliza com recuo igual à imagem de referência
            val isRow3NineKeys = keyboardMode == MODE_TEXT && rowIndex == 2 && row.size == 9

            val availableWidth = if (isRow3NineKeys) {
                (9 * standardKeyWidth) + (paddingH * 8)
            } else {
                w - (paddingH * (row.size + 1))
            }

            var currentX = if (isRow3NineKeys) {
                (w - availableWidth) / 2f
            } else {
                paddingH
            }

            for (keyDef in row) {
                val keyWidth = if (isRow3NineKeys) {
                    standardKeyWidth
                } else {
                    (keyDef.widthWeight / totalWeight) * availableWidth
                }
                keyDef.rect.set(currentX, rowTop, currentX + keyWidth, rowTop + rowHeight)

                if (keyDef.code > 0 && Character.isLetter(keyDef.code)) {
                    keyDef.label = if (shiftState != SHIFT_OFF) {
                        keyDef.label.uppercase()
                    } else {
                        keyDef.label.lowercase()
                    }
                }

                keys.add(keyDef)
                currentX += keyWidth + paddingH
            }
        }
    }

    private fun getTextRows(): List<List<Key>> {
        // Linha 1: Linha de Números dedicada
        val row1 = listOf(
            Key('1'.code, "1"), Key('2'.code, "2"), Key('3'.code, "3"), Key('4'.code, "4"),
            Key('5'.code, "5"), Key('6'.code, "6"), Key('7'.code, "7"), Key('8'.code, "8"),
            Key('9'.code, "9"), Key('0'.code, "0")
        )

        // Linha 2: q w e r t y u i o p
        val row2 = listOf(
            Key('q'.code, "q"),
            Key('w'.code, "w"),
            Key('e'.code, "e", accents = listOf("é", "ê", "è", "ë")),
            Key('r'.code, "r"),
            Key('t'.code, "t"),
            Key('y'.code, "y"),
            Key('u'.code, "u", accents = listOf("ú", "ü", "ù", "û")),
            Key('i'.code, "i", accents = listOf("í", "ì", "î", "ï")),
            Key('o'.code, "o", accents = listOf("ó", "ô", "õ", "ò", "ö")),
            Key('p'.code, "p")
        )

        // Linha 3: a s d f g h j k l (9 teclas com recuo idêntico à imagem de referência)
        val row3 = listOf(
            Key('a'.code, "a", accents = listOf("á", "à", "â", "ã", "ä")),
            Key('s'.code, "s", accents = listOf("ß")),
            Key('d'.code, "d"),
            Key('f'.code, "f"),
            Key('g'.code, "g"),
            Key('h'.code, "h"),
            Key('j'.code, "j"),
            Key('k'.code, "k"),
            Key('l'.code, "l")
        )

        // Linha 4: Shift, z x c v b n m, Backspace
        val row4 = listOf(
            Key(CODE_SHIFT, "⇧", widthWeight = 1.56f, isSpecial = true),
            Key('z'.code, "z"),
            Key('x'.code, "x"),
            Key('c'.code, "c", accents = listOf("ç")),
            Key('v'.code, "v"),
            Key('b'.code, "b"),
            Key('n'.code, "n", accents = listOf("ñ")),
            Key('m'.code, "m"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.56f, isSpecial = true)
        )

        // Linha 5: Botão de Símbolos ?123, vírgula, Emoji, Espaço, ponto, Ação
        val row5 = listOf(
            Key(CODE_MODE_CHANGE, "?123", widthWeight = 1.55f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_EMOJI, "😊", accents = listOf("😊", "😂", "❤️", "👍", "👏", "🙏", "😍", "🔥", "🎉", "😎"), widthWeight = 1.0f, isSpecial = true, isEmoji = true),
            Key(CODE_SPACE, "espaço", widthWeight = 4.38f),
            Key('.'.code, ".", widthWeight = 1.0f, isSpecial = true, accents = listOf("!", "?", "@", "-", "_", "/")),
            Key(CODE_ENTER, "action", widthWeight = 1.58f, isAction = true)
        )

        return listOf(row1, row2, row3, row4, row5)
    }

    private fun getSymbolRows(): List<List<Key>> {
        // Linha 1: Números
        val row1 = listOf(
            Key('1'.code, "1"), Key('2'.code, "2"), Key('3'.code, "3"), Key('4'.code, "4"),
            Key('5'.code, "5"), Key('6'.code, "6"), Key('7'.code, "7"), Key('8'.code, "8"),
            Key('9'.code, "9"), Key('0'.code, "0")
        )
        // Linha 2: Símbolos primários
        val row2 = listOf(
            Key('@'.code, "@"), Key('#'.code, "#"), Key('$'.code, "$", accents = listOf("R$", "€", "£")),
            Key('%'.code, "%"), Key('&'.code, "&"), Key('-'.code, "-"), Key('+'.code, "+"),
            Key('('.code, "("), Key(')'.code, ")"), Key('/'.code, "/")
        )
        // Linha 3: Pontuação e aspas
        val row3 = listOf(
            Key('*'.code, "*"), Key('"'.code, "\""), Key('\''.code, "'"), Key(':'.code, ":"),
            Key(';'.code, ";"), Key('!'.code, "!"), Key('?'.code, "?"), Key('['.code, "["),
            Key(']'.code, "]"), Key('\\'.code, "\\")
        )
        // Linha 4: Botão =\< (Acessar Mais Símbolos) e outros
        val row4 = listOf(
            Key(CODE_MORE_SYMBOLS, "=\\<", widthWeight = 1.56f, isSpecial = true),
            Key('~'.code, "~"), Key('`'.code, "`"), Key('<'.code, "<"), Key('>'.code, ">"),
            Key('='.code, "="), Key('_'.code, "_"), Key('|'.code, "|"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.56f, isSpecial = true)
        )
        // Linha 5: Botão ABC (retornar instantaneamente às letras)
        val row5 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.55f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_EMOJI, "😊", widthWeight = 1.0f, isSpecial = true, isEmoji = true),
            Key(CODE_SPACE, "espaço", widthWeight = 4.38f),
            Key('.'.code, ".", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_ENTER, "action", widthWeight = 1.58f, isAction = true)
        )
        return listOf(row1, row2, row3, row4, row5)
    }

    private fun getMoreSymbolRows(): List<List<Key>> {
        val row1 = listOf(
            Key('1'.code, "1"), Key('2'.code, "2"), Key('3'.code, "3"), Key('4'.code, "4"),
            Key('5'.code, "5"), Key('6'.code, "6"), Key('7'.code, "7"), Key('8'.code, "8"),
            Key('9'.code, "9"), Key('0'.code, "0")
        )
        val row2 = listOf(
            Key('~'.code, "~"), Key('`'.code, "`"), Key('|'.code, "|"), Key('\\'.code, "\\"),
            Key('^'.code, "^"), Key('°'.code, "°"), Key('•'.code, "•"), Key('·'.code, "·"),
            Key('…'.code, "…"), Key('§'.code, "§")
        )
        val row3 = listOf(
            Key('¿'.code, "¿"), Key('¡'.code, "¡"), Key('¢'.code, "¢"), Key('£'.code, "£"),
            Key('€'.code, "€"), Key('¥'.code, "¥"), Key('©'.code, "©"), Key('®'.code, "®"),
            Key('™'.code, "™"), Key('±'.code, "±")
        )
        // Linha 4: Botão ?123 para voltar aos símbolos anteriores
        val row4 = listOf(
            Key(CODE_MORE_SYMBOLS, "?123", widthWeight = 1.56f, isSpecial = true),
            Key('<'.code, "<"), Key('>'.code, ">"), Key('{'.code, "{"), Key('}'.code, "}"),
            Key('='.code, "="), Key('_'.code, "_"), Key('‰'.code, "‰"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.56f, isSpecial = true)
        )
        // Linha 5: Botão ABC para voltar às letras
        val row5 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.55f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_EMOJI, "😊", widthWeight = 1.0f, isSpecial = true, isEmoji = true),
            Key(CODE_SPACE, "espaço", widthWeight = 4.38f),
            Key('.'.code, ".", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_ENTER, "action", widthWeight = 1.58f, isAction = true)
        )
        return listOf(row1, row2, row3, row4, row5)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val density = resources.displayMetrics.density
        val keyRadius = 5f * density

        for (i in keys.indices) {
            val key = keys[i]
            val isPressed = isKeyPressed(key)

            val paint = when {
                key.isAction -> if (isPressed) actionPressedPaint else actionKeyPaint
                isPressed && !popupActive -> pressedKeyPaint
                key.code == CODE_MODE_CHANGE -> symbolKeyPaint
                key.isSpecial -> specialKeyPaint
                else -> keyPaint
            }

            // "?123" e enter são pílulas (raio = metade da altura); as demais teclas têm cantos de 5 dp
            val radius = if (key.code == CODE_MODE_CHANGE || key.code == CODE_ENTER) key.rect.height() / 2f else keyRadius
            canvas.drawRoundRect(key.rect, radius, radius, paint)

            // Renderização do conteúdo de cada tecla
            when (key.code) {
                CODE_SHIFT -> drawKeyIcon(canvas, key.rect, if (shiftState == SHIFT_OFF) iconShiftOutline else iconShift, shiftIconColor())
                CODE_BACKSPACE -> drawKeyIcon(canvas, key.rect, iconBackspace, Color.parseColor("#D9DBDC"))
                CODE_ENTER -> drawKeyIcon(canvas, key.rect, iconReturn, Color.WHITE)
                CODE_EMOJI -> drawKeyIcon(canvas, key.rect, iconEmoji, Color.parseColor("#D9DBDC"))
                CODE_SPACE -> {
                    // Barra de espaço limpa
                }
                else -> {
                    val isSpecialText = key.isSpecial || key.label.length > 1
                    val fontPaint = if (isSpecialText) specialTextPaint else textPaint

                    // Letras e dígitos: 52% da altura da tecla, reduzidos só se a largura passar de 74% da tecla
                    fontPaint.textSize = if (key.label.length > 2) key.rect.height() * 0.32f else
                        TextFit.fitSize(key.rect.height() * (if (key.label.firstOrNull()?.isDigit() == true) 0.62f else 0.72f),
                            key.rect.height() * 0.36f, key.rect.width() * 0.6f) { size ->
                            fontPaint.textSize = size
                            fontPaint.measureText(key.label)
                        }

                    val textY = key.rect.centerY() - ((fontPaint.descent() + fontPaint.ascent()) / 2)
                    canvas.drawText(key.label, key.rect.centerX(), textY, fontPaint)
                }
            }
        }

        // Desenhar popup de acentos se ativo
        if (popupActive && popupKey != null && popupRects.isNotEmpty()) {
            drawPopup(canvas)
        }
    }

    // Ícones originais do Material Icons (ver res/drawable/ic_kb_*)
    private val iconShift: Drawable? = loadIcon(R.drawable.ic_kb_shift)
    private val iconShiftOutline: Drawable? = loadIcon(R.drawable.ic_kb_shift_outline)
    private val iconBackspace: Drawable? = loadIcon(R.drawable.ic_kb_backspace)
    private val iconReturn: Drawable? = loadIcon(R.drawable.ic_kb_return)
    private val iconEmoji: Drawable? = loadIcon(R.drawable.ic_kb_emoji)

    private fun loadIcon(resId: Int): Drawable? {
        val raw = ContextCompat.getDrawable(context, resId) ?: return null
        return raw.mutate()
    }

    private fun shiftIconColor(): Int = when (shiftState) {
        SHIFT_LOCKED -> Color.WHITE
        SHIFT_ON -> Color.WHITE
        else -> Color.parseColor("#9DB1BC")
    }

    /** Desenha um ícone do Material centralizado na tecla, com a cor do estado atual. */
    private fun drawKeyIcon(canvas: Canvas, rect: RectF, icon: Drawable?, color: Int) {
        val d = icon ?: return
        val size = (rect.height() * 0.56f).toInt()
        val left = (rect.centerX() - size / 2f).toInt()
        val top = (rect.centerY() - size / 2f).toInt()
        d.setTint(color)
        d.setBounds(left, top, left + size, top + size)
        d.draw(canvas)
    }

    private fun isKeyPressed(key: Key): Boolean {
        for (info in activePointers.values) {
            if (info.key == key) return true
        }
        return false
    }

    /**
     * Acentos do popup em maiúscula quando a tecla está em maiúscula, seja pelo estado do shift
     * (inclusive caps lock) ou pelo rótulo que a tecla mostra na tela. Assim "E" com acento vira "É".
     */
    private fun isUpperPopup(key: Key): Boolean =
        shiftState != SHIFT_OFF || key.label.firstOrNull()?.isUpperCase() == true

    private fun calculatePopupLayout(key: Key) {
        popupRects.clear()
        val count = key.accents.size
        val itemWidth = key.rect.width().coerceAtLeast(64f)
        val itemHeight = key.rect.height()
        val totalWidth = count * itemWidth

        var startX = key.rect.centerX() - (totalWidth / 2f)
        if (startX < 8f) startX = 8f
        if (startX + totalWidth > width - 8f) startX = width - totalWidth - 8f
        val popupY = key.rect.top - itemHeight - 12f

        for (i in 0 until count) {
            val charText = if (isUpperPopup(key) && key.accents[i].length == 1 && Character.isLetter(key.accents[i][0])) {
                key.accents[i].uppercase()
            } else {
                key.accents[i]
            }
            val rect = RectF(
                startX + (i * itemWidth),
                popupY,
                startX + ((i + 1) * itemWidth),
                popupY + itemHeight
            )
            popupRects.add(Pair(charText, rect))
        }
    }

    private fun drawPopup(canvas: Canvas) {
        if (popupRects.isEmpty()) return
        val first = popupRects.first().second
        val last = popupRects.last().second
        tempRect.set(first.left - 6f, first.top - 6f, last.right + 6f, last.bottom + 6f)

        canvas.drawRoundRect(tempRect, 18f, 18f, popupBgPaint)

        for (i in popupRects.indices) {
            val (symbol, rect) = popupRects[i]
            if (i == selectedPopupIndex) {
                canvas.drawRoundRect(rect, 12f, 12f, actionKeyPaint)
            }
            textPaint.color = Color.WHITE
            textPaint.textSize = rect.height() * 0.42f
            val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(symbol, rect.centerX(), textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val key = findKey(x, y)

                // Em digitação rápida com múltiplos dedos, cancela qualquer long-press anterior
                handler.removeCallbacks(longPressRunnable)
                pendingLongPressPointerId = -1

                if (key != null) {
                    val now = SystemClock.uptimeMillis()

                    // Proteção contra toque duplo do hardware, para TODAS as teclas (inclusive Backspace e Shift)
                    // 1. Rejeição de ponteiro duplicado sobre a mesma tecla já pressionada
                    if (activeByKey.containsKey(key)) {
                        return true
                    }
                    // 2. Anti toque duplo global (80 ms), para qualquer tecla, inclusive "?123" e "ABC"
                    if (!TapGate.shared.accept(now)) {
                        return true
                    }

                    // Registra aceitação da tecla
                    activeByKey[key] = pointerId

                    val info = PointerInfo(key, x, y)
                    activePointers[pointerId] = info
                    triggerKeyVibration()

                    // Disparo instantâneo na detecção do toque (0ms de atraso)
                    when (key.code) {
                        CODE_SHIFT -> listener?.onShift()
                        CODE_BACKSPACE -> {
                            listener?.onBackspace()
                            backspaceDelay = 350L
                            handler.postDelayed(backspaceRunnable, backspaceDelay)
                        }
                        CODE_ENTER -> listener?.onActionKey()
                        CODE_MODE_CHANGE -> {
                            keyboardMode = if (keyboardMode == MODE_TEXT) MODE_SYMBOLS else MODE_TEXT
                        }
                        CODE_MORE_SYMBOLS -> {
                            keyboardMode = if (keyboardMode == MODE_SYMBOLS) MODE_MORE_SYMBOLS else MODE_SYMBOLS
                        }
                        CODE_EMOJI -> {
                            listener?.onKey(key.code, "😊")
                        }
                        CODE_SPACE -> listener?.onKey(CODE_SPACE, " ")
                        else -> {
                            listener?.onKey(key.code, key.label)
                        }
                    }

                    // Apenas programa long-press se não houver múltiplos dedos ativos rapidamente
                    if (key.accents.isNotEmpty() && activePointers.size == 1) {
                        pendingLongPressPointerId = pointerId
                        handler.postDelayed(longPressRunnable, longPressDelay)
                    }

                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (popupActive && pointerId == popupPointerId) {
                    val x = event.getX(actionIndex)
                    val y = event.getY(actionIndex)
                    var newIndex = -1
                    for (i in popupRects.indices) {
                        if (popupRects[i].second.contains(x, y)) {
                            newIndex = i
                            break
                        }
                    }
                    if (newIndex != selectedPopupIndex) {
                        selectedPopupIndex = newIndex
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRunnable)

                val info = activePointers[pointerId]
                if (info != null) {
                    activeByKey.remove(info.key)
                }

                if (popupActive && pointerId == popupPointerId) {
                    if (selectedPopupIndex >= 0 && selectedPopupIndex < popupRects.size) {
                        val chosen = popupRects[selectedPopupIndex].first
                        listener?.onReplaceKey(chosen)
                    }
                    popupActive = false
                    popupKey = null
                    selectedPopupIndex = -1
                    popupPointerId = -1
                    activePointers.remove(pointerId)
                    invalidate()
                    return true
                }

                activePointers.remove(pointerId)

                if (actionMasked == MotionEvent.ACTION_UP) {
                    activePointers.clear()
                    activeByKey.clear()
                    popupActive = false
                    popupKey = null
                    selectedPopupIndex = -1
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRunnable)
                activePointers.clear()
                activeByKey.clear()
                popupActive = false
                popupKey = null
                selectedPopupIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Algoritmo de tolerância e proximidade:
     * 1. Verifica toque direto dentro do retângulo da tecla.
     * 2. Se o toque ocorreu nos espaçamentos/gaps entre teclas durante a digitação rápida,
     *    encontra a tecla mais próxima para NUNCA perder letras ou gerar atraso percebido.
     */
    private fun findKey(x: Float, y: Float): Key? {
        // 1. Toque direto
        for (i in keys.indices) {
            if (keys[i].rect.contains(x, y)) {
                return keys[i]
            }
        }

        // 2. Proximidade com tolerância para digitação veloz
        var closestKey: Key? = null
        var minDistanceSq = Float.MAX_VALUE

        for (i in keys.indices) {
            val rect = keys[i].rect
            val dx = when {
                x < rect.left -> rect.left - x
                x > rect.right -> x - rect.right
                else -> 0f
            }
            val dy = when {
                y < rect.top -> rect.top - y
                y > rect.bottom -> y - rect.bottom
                else -> 0f
            }
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestKey = keys[i]
            }
        }

        // Se a distância for razoável (até 64px do teclado), seleciona a tecla mais próxima
        return if (minDistanceSq <= 4096f) closestKey else keys.firstOrNull()
    }
}

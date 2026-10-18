package com.example.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.R

/**
 * Barra superior do teclado, no layout da referência do Gboard:
 *
 *   [grade]   [ sugestões / pílula de captura ]   (mic)
 *
 * - Esquerda (grade): toque abre/fecha a linha de ferramentas; toque longo abre o menu.
 * - Centro: sugestões quando há palavra em digitação; pílula de captura de tela quando há uma recente;
 *   previsões da próxima palavra no resto do tempo.
 * - Direita: microfone num círculo.
 * - Linha de ferramentas (só depois do toque na grade): cursor, área de transferência, tradução, ajustes.
 *
 * A barra NUNCA troca de layout sozinha. Antes, ao terminar uma palavra, os ícones de ajustes e
 * tradução surgiam sob o dedo e abriam o app. Agora eles só existem dentro da linha de ferramentas.
 */
class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ToolbarAction { MENU, CURSOR, CLIPBOARD, TRANSLATE, SETTINGS, MIC }

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onToolbarActionClickListener: ((ToolbarAction) -> Unit)? = null
    var onToolbarOrderChanged: ((List<ToolbarAction>) -> Unit)? = null
    /** Toque longo na grade: abre o menu (tema, emoji, compartilhar, desfazer). */
    var onGridLongPress: (() -> Unit)? = null
    /** Toque na pílula de captura de tela. */
    var onScreenshotClick: (() -> Unit)? = null

    /** Quando true, a grade desenha X e o toque fecha o painel aberto. */
    var menuOpen: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Linha de ferramentas aberta (cursor, área de transferência, tradução, ajustes). */
    var toolsOpen: Boolean = false
        private set

    /** Compatibilidade: a reordenação das ferramentas é feita por toque longo + arraste. */
    var customizeMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var suggestions: List<String> = emptyList()
    private var composing = false
    private var screenshotLabel: String? = null
    private var screenshotThumb: Bitmap? = null

    private var toolActions: MutableList<ToolbarAction> = mutableListOf(
        ToolbarAction.CURSOR, ToolbarAction.CLIPBOARD, ToolbarAction.TRANSLATE, ToolbarAction.SETTINGS
    )

    // ---- Geometria (dp, medida na referência do Gboard a 2 px/dp) ----
    private val barHeightDp = 41f
    private val gridSlotDp = 41f
    private val gridIconDp = 18f
    private val micDiameterDp = 30f
    private val micRightMarginDp = 6.5f
    private val toolIconDp = 22f
    private val toolStepDp = 63f
    private val toolFirstCenterDp = 85f
    private val pillWidthDp = 135f
    private val pillHeightDp = 30f
    private val dividerHeightDp = 20f

    // ---- Estado de toque ----
    private enum class Hit { NONE, GRID, MIC, PILL, SUGGESTION, TOOL }

    private var pressedHit = Hit.NONE
    private var pressedIndex = -1
    private var downTime = 0L
    private var longPressDone = false
    private var dragFromIndex = -1

    private val gridRect = RectF()
    private val micRect = RectF()
    private val pillRect = RectF()
    private val suggestionRects = ArrayList<RectF>(3)
    private val toolRects = ArrayList<RectF>(4)

    // ---- Cores (paleta da referência; applyPalette pode sobrescrever) ----
    private var bgColor = Color.parseColor("#263238")
    private var iconTint = Color.parseColor("#C6C9CB")
    private val textColor = Color.parseColor("#D9DBDC")
    private val micBgColor = Color.parseColor("#404A50")
    private val pillBgColor = Color.parseColor("#3B464C")
    private val dividerColor = Color.parseColor("#404A50")
    private val pressColor = Color.parseColor("#404A50")

    private val bgPaint = Paint().apply { color = bgColor }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dividerColor; strokeWidth = 1f }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pressColor; style = Paint.Style.FILL }
    private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = micBgColor; style = Paint.Style.FILL }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBgColor; style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val thumbPath = Path()

    private val iconMap: MutableMap<ToolbarAction, Drawable?> = mutableMapOf()
    private var gridIcon: Drawable? = null
    private var closeIcon: Drawable? = null

    init {
        reloadIcons()
    }

    private fun reloadIcons() {
        gridIcon = loadTinted(R.drawable.ic_kb_grid)
        closeIcon = loadTinted(R.drawable.ic_close_24)
        iconMap[ToolbarAction.CURSOR] = loadTinted(R.drawable.ic_toolbar_cursor)
        iconMap[ToolbarAction.CLIPBOARD] = loadTinted(R.drawable.ic_kb_clipboard)
        iconMap[ToolbarAction.TRANSLATE] = loadTinted(R.drawable.ic_kb_translate)
        iconMap[ToolbarAction.SETTINGS] = loadTinted(R.drawable.ic_kb_settings)
        iconMap[ToolbarAction.MIC] = loadTinted(R.drawable.ic_kb_mic)
    }

    private fun loadTinted(resId: Int): Drawable? {
        val raw = ContextCompat.getDrawable(context, resId) ?: return null
        val d = DrawableCompat.wrap(raw.mutate())
        DrawableCompat.setTint(d, iconTint)
        return d
    }

    // ---- API pública usada pelo serviço ----

    fun setToolbarOrder(order: List<ToolbarAction>) {
        val tools = order.filter { it in TOOL_ACTIONS }.distinct()
        toolActions = (tools + TOOL_ACTIONS.filter { it !in tools }).toMutableList()
        calculateLayout()
        invalidate()
    }

    fun getToolbarOrder(): List<ToolbarAction> = toolActions.toList()

    fun applyPalette(p: KeyboardTheme.Palette) {
        bgColor = p.candidateBg
        bgPaint.color = bgColor
        iconTint = p.candidateIcon
        reloadIcons()
        invalidate()
    }

    /**
     * Sugestões do centro. [composing] = true quando há palavra em digitação (sugestões de completar);
     * false quando não há (previsões da próxima palavra, ou pílula de captura).
     */
    fun setSuggestions(list: List<String>, composing: Boolean = true) {
        suggestions = list.take(3)
        this.composing = composing
        calculateLayout()
        invalidate()
    }

    /** Pílula de captura de tela (null = sem captura recente). A pílula só aparece sem palavra em digitação. */
    fun setScreenshot(label: String?, thumb: Bitmap?) {
        screenshotLabel = label
        screenshotThumb = thumb
        calculateLayout()
        invalidate()
    }

    fun setToolsOpen(open: Boolean) {
        if (toolsOpen == open) return
        toolsOpen = open
        calculateLayout()
        invalidate()
    }

    // ---- Layout ----

    private val density get() = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (barHeightDp * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout()
    }

    private fun calculateLayout() {
        val d = density
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        gridRect.set(0f, 0f, gridSlotDp * d, h)
        val micCx = w - (micRightMarginDp + micDiameterDp / 2f) * d
        val micR = micDiameterDp * d / 2f
        micRect.set(micCx - micR, h / 2f - micR, micCx + micR, h / 2f + micR)

        val left = gridSlotDp * d
        val right = micRect.left - 4f * d
        suggestionRects.clear()
        toolRects.clear()
        pillRect.setEmpty()

        if (toolsOpen) {
            for (i in toolActions.indices) {
                val cx = (toolFirstCenterDp + i * toolStepDp) * d
                toolRects.add(RectF(cx - toolStepDp * d / 2f, 0f, cx + toolStepDp * d / 2f, h))
            }
            return
        }

        if (!composing && screenshotLabel != null) {
            val cx = (left + right) / 2f
            val pw = pillWidthDp * d
            val ph = pillHeightDp * d
            pillRect.set(cx - pw / 2f, h / 2f - ph / 2f, cx + pw / 2f, h / 2f + ph / 2f)
            return
        }

        val count = suggestions.size
        if (count > 0) {
            val slot = (right - left) / count
            for (i in 0 until count) {
                suggestionRects.add(RectF(left + i * slot, 0f, left + (i + 1) * slot, h))
            }
        }
    }

    // ---- Desenho ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = density
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Grade (ou X quando um painel está aberto)
        val gridPressed = pressedHit == Hit.GRID
        if (gridPressed) canvas.drawCircle(gridRect.centerX(), h / 2f, 15f * d, pressPaint)
        drawIconAt(canvas, if (menuOpen) closeIcon else gridIcon, gridRect.centerX(), h / 2f, gridIconDp * d)

        // Microfone num círculo
        canvas.drawCircle(micRect.centerX(), micRect.centerY(), micRect.width() / 2f, micPaint)
        if (pressedHit == Hit.MIC) canvas.drawCircle(micRect.centerX(), micRect.centerY(), micRect.width() / 2f, pressPaint)
        drawIconAt(canvas, iconMap[ToolbarAction.MIC], micRect.centerX(), micRect.centerY(), 17f * d)

        if (toolsOpen) {
            for (i in toolRects.indices) {
                val r = toolRects[i]
                if (pressedHit == Hit.TOOL && pressedIndex == i) {
                    canvas.drawCircle(r.centerX(), h / 2f, 16f * d, pressPaint)
                }
                drawIconAt(canvas, iconMap[toolActions[i]], r.centerX(), h / 2f, toolIconDp * d)
            }
            return
        }

        if (!composing && pillRect.width() > 0f) {
            drawPill(canvas, d)
            return
        }

        textPaint.textSize = 16f * resources.displayMetrics.scaledDensity
        for (i in suggestionRects.indices) {
            val r = suggestionRects[i]
            if (pressedHit == Hit.SUGGESTION && pressedIndex == i) {
                canvas.drawRoundRect(RectF(r.left + 4f * d, 3f * d, r.right - 4f * d, h - 3f * d), 15f * d, 15f * d, pressPaint)
            }
            val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(suggestions[i], r.centerX(), y, textPaint)
            if (i < suggestionRects.size - 1) {
                val x = r.right
                val half = dividerHeightDp * d / 2f
                canvas.drawLine(x, h / 2f - half, x, h / 2f + half, dividerPaint)
            }
        }
    }

    private fun drawPill(canvas: Canvas, d: Float) {
        val r = pillRect
        canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, pillPaint)
        if (pressedHit == Hit.PILL) {
            canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, pressPaint)
        }
        val thumbSize = 22f * d
        val thumbLeft = r.left + 4f * d
        val thumbTop = r.centerY() - thumbSize / 2f
        val thumb = screenshotThumb
        if (thumb != null) {
            thumbPath.reset()
            thumbPath.addRoundRect(
                RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize),
                4f * d, 4f * d, Path.Direction.CW
            )
            canvas.save()
            canvas.clipPath(thumbPath)
            canvas.drawBitmap(thumb, null, RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize), null)
            canvas.restore()
        }
        val label = screenshotLabel ?: return
        pillTextPaint.textSize = 14f * resources.displayMetrics.scaledDensity
        val textLeft = thumbLeft + thumbSize + 6f * d
        val maxW = (r.right - 8f * d) - textLeft
        val fitted = TextUtils.ellipsize(label, pillTextPaint, maxW, TextUtils.TruncateAt.END).toString()
        val y = r.centerY() - (pillTextPaint.descent() + pillTextPaint.ascent()) / 2f
        canvas.drawText(fitted, textLeft, y, pillTextPaint)
    }

    private fun drawIconAt(canvas: Canvas, d: Drawable?, cx: Float, cy: Float, sizePx: Float) {
        val dr = d ?: return
        val half = (sizePx / 2f).toInt()
        val left = (cx - half).toInt()
        val top = (cy - half).toInt()
        dr.setBounds(left, top, left + 2 * half, top + 2 * half)
        dr.draw(canvas)
    }

    // ---- Toque ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                longPressDone = false
                dragFromIndex = -1
                val hit = hitTest(x, y)
                pressedHit = hit.first
                pressedIndex = hit.second
                if (pressedHit == Hit.TOOL) dragFromIndex = pressedIndex
                invalidate()
                return pressedHit != Hit.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                val elapsed = SystemClock.uptimeMillis() - downTime
                if (pressedHit == Hit.GRID && !longPressDone && elapsed >= LONG_PRESS_MS) {
                    longPressDone = true
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onGridLongPress?.invoke()
                    pressedHit = Hit.NONE
                    invalidate()
                    return true
                }
                if (pressedHit == Hit.TOOL && dragFromIndex >= 0 && elapsed >= LONG_PRESS_MS) {
                    // Arraste de ferramentas: troca de lugar com a ferramenta sob o dedo
                    val over = hitTest(x, y)
                    if (over.first == Hit.TOOL && over.second != dragFromIndex) {
                        val item = toolActions.removeAt(dragFromIndex)
                        toolActions.add(over.second, item)
                        dragFromIndex = over.second
                        pressedIndex = over.second
                        calculateLayout()
                        onToolbarOrderChanged?.invoke(toolActions.toList())
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val hit = hitTest(x, y)
                val fired = !longPressDone && hit.first == pressedHit && hit.second == pressedIndex
                when (pressedHit) {
                    Hit.GRID -> if (fired) setToolsOpen(!toolsOpen)
                    Hit.MIC -> if (fired) onToolbarActionClickListener?.invoke(ToolbarAction.MIC)
                    Hit.PILL -> if (fired) onScreenshotClick?.invoke()
                    Hit.SUGGESTION -> if (fired) suggestions.getOrNull(pressedIndex)?.let { onSuggestionClickListener?.invoke(it) }
                    Hit.TOOL -> if (fired && dragFromIndex >= 0 && elapsed() < LONG_PRESS_MS) {
                        onToolbarActionClickListener?.invoke(toolActions[pressedIndex])
                    }
                    Hit.NONE -> {}
                }
                clearPress()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                clearPress()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun elapsed() = SystemClock.uptimeMillis() - downTime

    private fun clearPress() {
        pressedHit = Hit.NONE
        pressedIndex = -1
        dragFromIndex = -1
        longPressDone = false
        invalidate()
    }

    /** Diz o que está sob o ponto (x, y): região e índice. Só considera o que está visível. */
    private fun hitTest(x: Float, y: Float): Pair<Hit, Int> {
        if (gridRect.contains(x, y)) return Hit.GRID to -1
        if (x >= micRect.left - 6f * density) return Hit.MIC to -1
        if (toolsOpen) {
            for (i in toolRects.indices) if (toolRects[i].contains(x, y)) return Hit.TOOL to i
            return Hit.NONE to -1
        }
        if (pillRect.width() > 0f && pillRect.contains(x, y)) return Hit.PILL to -1
        for (i in suggestionRects.indices) if (suggestionRects[i].contains(x, y)) return Hit.SUGGESTION to i
        return Hit.NONE to -1
    }

    companion object {
        private const val LONG_PRESS_MS = 450L
        private val TOOL_ACTIONS = listOf(
            ToolbarAction.CURSOR, ToolbarAction.CLIPBOARD, ToolbarAction.TRANSLATE, ToolbarAction.SETTINGS
        )

        fun parseOrder(raw: String): List<ToolbarAction> {
            val parsed = raw.split(',')
                .mapNotNull { name -> runCatching { ToolbarAction.valueOf(name.trim()) }.getOrNull() }
                .filter { it in TOOL_ACTIONS }
                .distinct()
            return parsed + TOOL_ACTIONS.filter { it !in parsed }
        }

        fun serializeOrder(order: List<ToolbarAction>): String =
            order.filter { it in TOOL_ACTIONS }.joinToString(",") { it.name }
    }
}

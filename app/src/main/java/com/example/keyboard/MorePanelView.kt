package com.example.keyboard

import com.example.onTap

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Painel de opções no lugar das teclas (menu do ícone view_cozy).
 * Chips reais: Tema, Emoji, Compartilhar, Desfazer.
 */
class MorePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    enum class Action { THEME, EMOJI, SHARE, UNDO }

    var onAction: ((Action) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#263238"))
        setPadding(dp(12), dp(12), dp(12), dp(16))

        val hint = TextView(context).apply {
            text = "Mantenha pressionado e arraste os ícones da barra para personalizar"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(dp(4), 0, dp(4), dp(12))
        }
        addView(hint)

        val grid = GridLayout(context).apply {
            columnCount = 2
            rowCount = 2
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val items = listOf(
            Action.THEME to "🎨  Tema",
            Action.EMOJI to "😊  Emoji",
            Action.SHARE to "🔗  Compartilhar",
            Action.UNDO to "↩  Desfazer"
        )
        for ((action, label) in items) {
            grid.addView(makeChip(label) { onAction?.invoke(action) })
        }
        addView(grid)
    }

    private fun makeChip(label: String, click: () -> Unit): TextView {
        val lp = GridLayout.LayoutParams().apply {
            width = 0
            height = dp(52)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(6), dp(6), dp(6), dp(6))
        }
        return TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(Color.parseColor("#404A50"))
            layoutParams = lp
            isClickable = true
            isFocusable = true
            onTap { click() }
        }
    }

    fun applyPalette(p: KeyboardTheme.Palette) {
        setBackgroundColor(p.background)
        // chips já usam cores fixas legíveis; fundo do painel acompanha o tema
    }
}

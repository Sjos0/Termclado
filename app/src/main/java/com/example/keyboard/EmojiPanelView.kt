package com.example.keyboard

import com.example.onTap

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Painel leve de emojis (sem biblioteca). Toque insere o caractere via callback.
 */
class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onEmoji: ((String) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val categories = listOf(
        "Frequentes" to listOf(
            "😀", "😂", "🥰", "😍", "🤔", "😎", "😢", "😡",
            "👍", "👎", "👏", "🙏", "🔥", "✨", "❤️", "💔",
            "🎉", "✅", "❌", "⭐", "💯", "👀", "💪", "🤝"
        ),
        "Rostos" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘",
            "😗", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪",
            "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
            "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😮‍💨",
            "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒"
        ),
        "Gestos" to listOf(
            "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️",
            "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆",
            "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖", "👏",
            "🙌", "🤲", "🤝", "🙏", "💪", "🦾", "💅", "✍️"
        ),
        "Objetos" to listOf(
            "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "💾", "📷",
            "🎥", "📺", "📻", "🔋", "🔌", "💡", "🔦", "📖",
            "✏️", "🖊️", "📝", "📁", "📂", "📅", "⏰", "🔒"
        )
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#263238"))

        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(context).apply {
            text = "← Voltar"
            setTextColor(Color.parseColor("#7FCFFF"))
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            onTap { onBack?.invoke() }
        }
        top.addView(back)
        addView(top)

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), 0, dp(8), dp(12))
        }

        for ((title, emojis) in categories) {
            body.addView(TextView(context).apply {
                text = title
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(dp(4), dp(10), dp(4), dp(4))
            })
            val grid = GridLayout(context).apply {
                columnCount = 8
            }
            for (e in emojis) {
                grid.addView(TextView(context).apply {
                    text = e
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(8), dp(6), dp(8))
                    onTap { onEmoji?.invoke(e) }
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = dp(44)
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                })
            }
            body.addView(grid)
        }
        scroll.addView(body)
        addView(scroll)
    }

    fun applyPalette(p: KeyboardTheme.Palette) {
        setBackgroundColor(p.background)
    }
}

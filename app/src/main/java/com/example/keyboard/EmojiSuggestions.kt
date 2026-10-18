package com.example.keyboard

import android.content.Context
import java.text.Normalizer

/**
 * Emojis sugeridos para uma palavra completa em português, como o Gboard faz
 * (ex.: "teclado" sugere ⌨ e 🎹). Dados: anotações de emoji do Unicode CLDR (pt),
 * ver assets/emoji_pt_br.tsv e NOTICE.
 */
object EmojiSuggestions {
    @Volatile private var table: Map<String, List<String>>? = null

    /** Carrega a tabela uma vez (1.9 mil palavras, poucos KB). */
    fun init(context: Context) {
        if (table != null) return
        table = try {
            context.assets.open("emoji_pt_br.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size == 2) parts[0] to parts[1].split('|').filter { it.isNotEmpty() } else null
                }.toMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Emojis cujo nome oficial ou palavra-chave é exatamente [word] (sem acento e minúsculas). */
    fun forWord(word: String): List<String> {
        val key = normalize(word)
        if (key.isEmpty()) return emptyList()
        return table?.get(key) ?: emptyList()
    }

    private fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        return decomposed.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    }
}

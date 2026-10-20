package com.example.keyboard

/**
 * Tamanho, em code units UTF-16, do último caractere visível antes do cursor.
 * Um emoji tem 2 code units (surrogate pair): apagar só 1 deixa metade do par e o editor mostra tofu.
 * Também cobre modificadores de tom de pele, seletor de variação (FE0F) e sequências ZWJ (👨‍👩).
 */
object Graphemes {
    private const val ZWJ = 0x200D
    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private const val SKIN_TONE_FIRST = 0x1F3FB
    private const val SKIN_TONE_LAST = 0x1F3FF

    fun lastClusterLength(text: CharSequence): Int {
        var p = text.length
        while (p > 0) {
            val cp = Character.codePointBefore(text, p)
            p -= Character.charCount(cp)
            // modificadores pertencem ao caractere base que vem antes
            if (cp == VARIATION_SELECTOR_16 || cp in SKIN_TONE_FIRST..SKIN_TONE_LAST) continue
            // base + ZWJ + base: a base anterior faz parte da mesma sequência
            if (p > 0 && Character.codePointBefore(text, p) == ZWJ) {
                p -= 1
                continue
            }
            break
        }
        return text.length - p
    }
}

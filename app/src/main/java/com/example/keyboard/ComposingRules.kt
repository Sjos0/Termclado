package com.example.keyboard

/** Regras puras da palavra em composição, testáveis sem Android. */
object ComposingRules {
    /**
     * True se o texto antes do cursor ainda termina com a palavra em composição.
     * Se o usuário moveu o cursor para outro ponto, o texto antes dele deixa de
     * terminar com a palavra, e a composição precisa ser encerrada.
     */
    fun stillAtCursor(textBeforeCursor: String, composing: String): Boolean =
        composing.isNotEmpty() && textBeforeCursor.endsWith(composing)

    /**
     * True se o editor informa um trecho de composição que não termina no cursor.
     * candidatesStart/candidatesEnd valem -1 quando o editor não informa composição.
     */
    fun compositionElsewhere(candidatesEnd: Int, cursor: Int): Boolean =
        candidatesEnd != -1 && candidatesEnd != cursor
}

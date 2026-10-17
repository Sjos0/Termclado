package com.example

/**
 * Anti toque duplo global, para o app e para o teclado.
 *
 * Um toque só é aceito se passou pelo menos [windowMs] desde o último toque ACEITO,
 * em qualquer botão. Toques rejeitados não reiniciam a janela, então uma sequência
 * rápida de toques não trava o teclado.
 */
class TapGate(private val windowMs: Long = WINDOW_MS) {
    private var hasAccepted = false
    private var lastAcceptedMs = 0L

    @Synchronized
    fun accept(nowMs: Long): Boolean {
        if (hasAccepted && nowMs - lastAcceptedMs < windowMs) return false
        hasAccepted = true
        lastAcceptedMs = nowMs
        return true
    }

    companion object {
        const val WINDOW_MS = 80L
        val shared = TapGate()
    }
}

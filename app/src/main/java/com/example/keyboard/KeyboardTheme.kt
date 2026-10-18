package com.example.keyboard

import android.graphics.Color

/**
 * Paletas leves do teclado (sem dependências). Índice persistido em KeyboardPreferences.themeId.
 */
object KeyboardTheme {
    data class Palette(
        val name: String,
        val background: Int,
        val key: Int,
        val specialKey: Int,
        val pressedKey: Int,
        val actionKey: Int,
        val actionPressed: Int,
        val text: Int,
        val emojiBorder: Int,
        val candidateBg: Int,
        val candidateIcon: Int
    )

    val palettes: List<Palette> = listOf(
        // 0 — Grafite & Cobalto (atual)
        Palette(
            name = "Grafite e Cobalto",
            background = Color.parseColor("#263238"),
            key = Color.parseColor("#404A50"),
            specialKey = Color.parseColor("#313C42"),
            pressedKey = Color.parseColor("#546068"),
            actionKey = Color.parseColor("#6EACA8"),
            actionPressed = Color.parseColor("#5A9592"),
            text = Color.parseColor("#FFFFFF"),
            emojiBorder = Color.parseColor("#7FCFFF"),
            candidateBg = Color.parseColor("#263238"),
            candidateIcon = Color.parseColor("#B0BEC5")
        ),
        // 1 — OLED puro (preto)
        Palette(
            name = "OLED Preto",
            background = Color.parseColor("#000000"),
            key = Color.parseColor("#1C1C1E"),
            specialKey = Color.parseColor("#2C2C2E"),
            pressedKey = Color.parseColor("#3A3A3C"),
            actionKey = Color.parseColor("#0A84FF"),
            actionPressed = Color.parseColor("#0066CC"),
            text = Color.parseColor("#F2F2F7"),
            emojiBorder = Color.parseColor("#64D2FF"),
            candidateBg = Color.parseColor("#000000"),
            candidateIcon = Color.parseColor("#AEAEB2")
        ),
        // 2 — Ardósia azul
        Palette(
            name = "Ardósia Azul",
            background = Color.parseColor("#1A2332"),
            key = Color.parseColor("#2D3A4F"),
            specialKey = Color.parseColor("#243044"),
            pressedKey = Color.parseColor("#3D4F6A"),
            actionKey = Color.parseColor("#5B8DEF"),
            actionPressed = Color.parseColor("#3D6FD4"),
            text = Color.parseColor("#E8EEF7"),
            emojiBorder = Color.parseColor("#8BB4FF"),
            candidateBg = Color.parseColor("#1A2332"),
            candidateIcon = Color.parseColor("#A8B8CC")
        )
    )

    fun get(id: Int): Palette = palettes[id.coerceIn(0, palettes.lastIndex)]

    fun nextId(current: Int): Int = (current + 1) % palettes.size
}
